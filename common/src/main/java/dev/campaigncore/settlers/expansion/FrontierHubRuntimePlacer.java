package dev.campaigncore.settlers.expansion;

import dev.architectury.event.events.common.TickEvent;
import dev.architectury.event.events.common.LifecycleEvent;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.detection.SettlementConverter;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Places the authored frontier hub through Minecraft's jigsaw structure generator, but fits completed
 * terrain around the generated plan before committing its pieces. This is a bounded runtime analogue
 * of the structure's {@code beard_thin} noise-stage terrain adaptation; it does not rerun chunk noise.
 */
public final class FrontierHubRuntimePlacer {
    public static final ResourceLocation FRONTIER_HUB =
            ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "frontier_hub");
    static final int BLEND_RADIUS = 10;
    private static final int MAX_VERTICAL_CHANGE = 24;
    private static final int MAX_AFFECTED_COLUMNS = 65_536;
    private static final int UPDATE_FLAGS = 2;
    // Leaves most of a normal 50 ms server tick available to Minecraft while completing manual
    // placement considerably faster than the original ultra-conservative 5 ms slice.
    private static final long JOB_BUDGET_NANOS = 25_000_000L;
    private static final Map<ResourceKey<Level>, PlacementJob> ACTIVE_JOBS = new LinkedHashMap<>();

    private FrontierHubRuntimePlacer() {
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(FrontierHubRuntimePlacer::tickJobs);
        LifecycleEvent.SERVER_LEVEL_UNLOAD.register(
                level -> ACTIVE_JOBS.remove(level.dimension()));
    }

    public static StartResult enqueue(
            CommandSourceStack source, BlockPos requestedPosition, boolean ruined
    ) {
        ResourceKey<Level> dimension = source.getLevel().dimension();
        if (ACTIVE_JOBS.containsKey(dimension)) {
            return StartResult.rejected("Another frontier hub placement is already active in this dimension.");
        }
        ACTIVE_JOBS.put(dimension, new PlacementJob(source, requestedPosition, ruined));
        return StartResult.accepted();
    }

    public static Optional<JobStatus> status(ServerLevel level) {
        PlacementJob job = ACTIVE_JOBS.get(level.dimension());
        return job == null ? Optional.empty() : Optional.of(job.status());
    }

    public static CancelResult cancel(ServerLevel level) {
        PlacementJob job = ACTIVE_JOBS.get(level.dimension());
        if (job == null) {
            return CancelResult.rejected("No frontier hub placement is active in this dimension.");
        }
        if (job.mutatedWorld) {
            return CancelResult.rejected(
                    "Placement has already changed the world and cannot be cancelled safely.");
        }
        ACTIVE_JOBS.remove(level.dimension());
        return CancelResult.cancelled();
    }

    private static void tickJobs(ServerLevel level) {
        PlacementJob job = ACTIVE_JOBS.get(level.dimension());
        if (job == null) {
            return;
        }
        try {
            job.tick(level);
        } catch (RuntimeException exception) {
            SettlersMod.LOGGER.error("Frontier hub placement job failed.", exception);
            job.fail("Unexpected placement error: " + exception.getMessage());
        }
        if (job.finished) {
            ACTIVE_JOBS.remove(level.dimension());
        }
    }

    public static PlacementResult place(ServerLevel level, BlockPos requestedPosition) {
        GeneratedHub generated = generate(level, requestedPosition);
        if (!generated.success()) {
            return PlacementResult.failure(generated.message());
        }

        StructureStart start = generated.start();
        Optional<String> validationFailure = validateBounds(level, start, start.getBoundingBox());
        if (validationFailure.isPresent()) {
            return PlacementResult.failure(validationFailure.get());
        }
        int changedBlocks = fitTerrain(level, TerrainPlan.from(start));
        placePieces(level, generated.generator(), start);

        Settlement settlement = SettlementConverter.convert(level, start, "frontier_hub");
        SettlersMod.LOGGER.info(
                "Runtime-placed frontier hub '{}' at {} with {} pieces and {} terrain changes.",
                settlement.name(), settlement.center(), start.getPieces().size(), changedBlocks);
        return PlacementResult.success(settlement, start.getPieces().size(), changedBlocks);
    }

    /**
     * Places a deliberately smaller, abandoned version of the hub. It is scenery, not a dormant
     * settlement: no settlement record is created, so population, economy, threats, and expansion
     * never start for it.
     */
    public static RuinPlacementResult placeRuined(ServerLevel level, BlockPos requestedPosition) {
        GeneratedHub generated = generate(level, requestedPosition);
        if (!generated.success()) {
            return RuinPlacementResult.failure(generated.message());
        }

        List<StructurePiece> selected = selectRuinedPieces(
                generated.start().getPieces(), requestedPosition);
        StructureStart ruin = new StructureStart(
                generated.structure(),
                generated.start().getChunkPos(),
                0,
                new PiecesContainer(selected));

        BoundingBox bounds = ruin.getBoundingBox();
        Optional<String> validationFailure = validateBounds(level, ruin, bounds);
        if (validationFailure.isPresent()) {
            return RuinPlacementResult.failure(validationFailure.get());
        }

        int terrainChanges = fitTerrain(level, TerrainPlan.from(ruin));
        placePieces(level, generated.generator(), ruin);
        int decayChanges = weatherRuin(level, bounds);

        SettlersMod.LOGGER.info(
                "Runtime-placed ruined frontier hub at {} with {}/{} pieces, {} terrain changes, "
                        + "and {} decay changes. No settlement was registered.",
                requestedPosition, selected.size(), generated.start().getPieces().size(),
                terrainChanges, decayChanges);
        return RuinPlacementResult.success(bounds, selected.size(),
                generated.start().getPieces().size(), terrainChanges, decayChanges);
    }

    private static GeneratedHub generate(ServerLevel level, BlockPos requestedPosition) {
        if (level.dimension() != Level.OVERWORLD) {
            return GeneratedHub.failure("The frontier hub can only be placed in the Overworld.");
        }

        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ResourceKey<Structure> structureKey = ResourceKey.create(Registries.STRUCTURE, FRONTIER_HUB);
        Optional<Holder.Reference<Structure>> holder = structures.getHolder(structureKey);
        if (holder.isEmpty()) {
            return GeneratedHub.failure("The settlers:frontier_hub structure is not registered.");
        }

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Structure structure = holder.get().value();
        StructureStart start = structure.generate(
                level.registryAccess(),
                generator,
                generator.getBiomeSource(),
                level.getChunkSource().randomState(),
                level.getStructureManager(),
                level.getSeed(),
                new ChunkPos(requestedPosition),
                0,
                level,
                biome -> true
        );
        if (!start.isValid() || start.getPieces().isEmpty()) {
            return GeneratedHub.failure("Minecraft could not assemble a frontier hub at this position.");
        }

        return GeneratedHub.success(structure, generator, start);
    }

    private static Optional<String> validateBounds(
            ServerLevel level, StructureStart start, BoundingBox bounds
    ) {
        int minX = bounds.minX() - BLEND_RADIUS;
        int minZ = bounds.minZ() - BLEND_RADIUS;
        int maxX = bounds.maxX() + BLEND_RADIUS;
        int maxZ = bounds.maxZ() + BLEND_RADIUS;
        long columns = (long) (maxX - minX + 1) * (maxZ - minZ + 1);
        if (columns > MAX_AFFECTED_COLUMNS) {
            return Optional.of("The generated hub is too large for bounded runtime terrain fitting.");
        }
        if (!level.hasChunksAt(minX, minZ, maxX, maxZ)) {
            return Optional.of(
                    "All hub and terrain-blend chunks must be loaded. Increase view distance or move closer.");
        }
        if (SettlementManager.get(level).isConverted(start.getChunkPos())) {
            return Optional.of("A settlement is already registered in the selected start chunk.");
        }
        for (Settlement existing : SettlementManager.get(level).all()) {
            if (existing.bounds().intersects(bounds)) {
                return Optional.of("The proposed frontier hub overlaps an existing settlement.");
            }
        }

        if (TerrainPlan.from(start).coreColumns().isEmpty()) {
            return Optional.of("The generated hub contained no terrain-fit-capable jigsaw pieces.");
        }
        return Optional.empty();
    }

    static int ruinedPieceCount(int completePieceCount) {
        return Math.max(1, (completePieceCount + 1) / 2);
    }

    private static List<StructurePiece> selectRuinedPieces(
            List<StructurePiece> complete, BlockPos requestedPosition
    ) {
        List<StructurePiece> nearest = new ArrayList<>(complete);
        nearest.sort(Comparator
                .comparingDouble((StructurePiece piece) ->
                        piece.getBoundingBox().getCenter().distSqr(requestedPosition))
                .thenComparingInt(piece -> piece.getBoundingBox().minX())
                .thenComparingInt(piece -> piece.getBoundingBox().minZ())
                .thenComparingInt(piece -> piece.getBoundingBox().minY()));
        return List.copyOf(nearest.subList(0, ruinedPieceCount(nearest.size())));
    }

    private static int fitTerrain(ServerLevel level, TerrainPlan plan) {
        int changed = 0;
        for (long column : plan.affectedColumns()) {
            changed += fitTerrainColumn(
                    level, plan, ChunkPos.getX(column), ChunkPos.getZ(column));
        }
        return changed;
    }

    private static int fitTerrainColumn(ServerLevel level, TerrainPlan plan, int x, int z) {
        ColumnTarget nearest = plan.nearestTargets().get(columnKey(x, z));
        if (nearest == null) {
            return 0;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int changed = 0;
        int currentSurface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        int targetSurface = blendHeight(currentSurface, nearest.surfaceY(), nearest.distance(), BLEND_RADIUS);
        targetSurface = Mth.clamp(targetSurface,
                currentSurface - MAX_VERTICAL_CHANGE, currentSurface + MAX_VERTICAL_CHANGE);
        targetSurface = Mth.clamp(targetSurface,
                level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - 2);

        BlockState oldSurface = level.getBlockState(cursor.set(x, currentSurface, z));
        BlockState oldSubsoil = level.getBlockState(cursor.set(x, currentSurface - 1, z));
        BlockState surface = suitableTerrain(level, cursor.set(x, currentSurface, z), oldSurface)
                ? oldSurface : Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState subsoil = suitableTerrain(level, cursor.set(x, currentSurface - 1, z), oldSubsoil)
                ? oldSubsoil : Blocks.DIRT.defaultBlockState();

        int clearTo = Math.max(currentSurface, targetSurface);
        ColumnPlan core = plan.coreColumns().get(columnKey(x, z));
        if (core != null) {
            clearTo = Math.max(clearTo, core.clearToY());
        }
        for (int y = clearTo; y > targetSurface; y--) {
            cursor.set(x, y, z);
            if (!level.getBlockState(cursor).isAir()) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
                changed++;
            }
        }
        for (int y = currentSurface + 1; y <= targetSurface; y++) {
            cursor.set(x, y, z);
            BlockState replacement = y == targetSurface ? surface : subsoil;
            if (!level.getBlockState(cursor).equals(replacement)) {
                level.setBlock(cursor, replacement, UPDATE_FLAGS);
                changed++;
            }
        }
        cursor.set(x, targetSurface, z);
        if (!level.getBlockState(cursor).equals(surface)) {
            level.setBlock(cursor, surface, UPDATE_FLAGS);
            changed++;
        }
        return changed;
    }

    private static boolean suitableTerrain(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty()
                && !state.is(Blocks.BEDROCK) && !state.is(Blocks.BARRIER)
                && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Linear outward blend, kept package-visible for deterministic unit tests. */
    static int blendHeight(int existing, int structure, double distance, int radius) {
        if (distance <= 0.0) {
            return structure;
        }
        double existingWeight = Mth.clamp(distance / radius, 0.0, 1.0);
        return (int) Math.round(Mth.lerp(existingWeight, structure, existing));
    }

    private static void placePieces(ServerLevel level, ChunkGenerator generator, StructureStart start) {
        BoundingBox bounds = start.getBoundingBox();
        ChunkPos min = new ChunkPos(bounds.minX() >> 4, bounds.minZ() >> 4);
        ChunkPos max = new ChunkPos(bounds.maxX() >> 4, bounds.maxZ() >> 4);
        ChunkPos.rangeClosed(min, max).forEach(chunk -> start.placeInChunk(
                level,
                level.structureManager(),
                generator,
                level.getRandom(),
                new BoundingBox(
                        chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                        chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()),
                chunk));
    }

    private static int weatherRuin(ServerLevel level, BoundingBox bounds) {
        int changed = 0;
        long seed = level.getSeed();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    cursor.set(x, y, z);
                    changed += weatherRuinBlock(level, bounds, cursor, seed);
                }
            }
        }
        return changed;
    }

    private static int weatherRuinBlock(
            ServerLevel level, BoundingBox bounds, BlockPos pos, long seed
    ) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return 0;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof Container container) {
            container.clearContent();
            blockEntity.setChanged();
            return 0;
        }

        int changed = 0;
        BlockState weathered = weatheredState(state, seed, pos.getX(), pos.getY(), pos.getZ());
        if (weathered != state) {
            level.setBlock(pos, weathered, UPDATE_FLAGS);
            changed++;
            state = weathered;
        }
        if (state.isAir() || preserveFromRemoval(state)) {
            return changed;
        }

        int height = Math.max(1, bounds.maxY() - bounds.minY());
        double vertical = (double) (pos.getY() - bounds.minY()) / height;
        int removalPercent = vertical >= 0.65 ? 38 : vertical >= 0.35 ? 20 : 8;
        if (isFragile(state)) {
            removalPercent = Math.max(removalPercent, 55);
        }
        if (ruinRoll(seed, pos.getX(), pos.getY(), pos.getZ(), 31) < removalPercent) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), UPDATE_FLAGS);
            changed++;
        }
        return changed;
    }

    private static BlockState weatheredState(BlockState state, long seed, int x, int y, int z) {
        int roll = ruinRoll(seed, x, y, z, 17);
        if (state.is(Blocks.FARMLAND) || state.is(Blocks.DIRT_PATH)) {
            return roll < 55 ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.DIRT.defaultBlockState();
        }
        if (state.is(Blocks.COBBLESTONE) && roll < 35) {
            return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
        }
        if (state.is(Blocks.STONE_BRICKS)) {
            return roll < 22 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState()
                    : roll < 42 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : state;
        }
        return state;
    }

    private static boolean preserveFromRemoval(BlockState state) {
        Block block = state.getBlock();
        return state.is(Blocks.BEDROCK)
                || state.is(Blocks.BARRIER)
                || state.is(Blocks.STRUCTURE_BLOCK)
                || state.is(Blocks.JIGSAW)
                || block instanceof WallBlock;
    }

    private static boolean isFragile(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof TrapDoorBlock
                || block instanceof FenceBlock
                || block instanceof FenceGateBlock
                || block instanceof LanternBlock
                || block instanceof BedBlock
                || block instanceof StainedGlassPaneBlock
                || state.is(Blocks.GLASS)
                || state.is(Blocks.GLASS_PANE)
                || state.is(Blocks.TORCH)
                || state.is(Blocks.WALL_TORCH);
    }

    static int ruinRoll(long seed, int x, int y, int z, int salt) {
        long value = seed ^ (long) x * 0x9E3779B97F4A7C15L
                ^ (long) y * 0xC2B2AE3D27D4EB4FL
                ^ (long) z * 0x165667B19E3779F9L
                ^ (long) salt * 0x85EBCA77C2B2AE63L;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (int) Math.floorMod(value, 100L);
    }

    private enum JobPhase {
        PREPARING("preparing"),
        TERRAIN("fitting terrain"),
        STRUCTURES("placing structures"),
        DECAY("weathering ruin"),
        FINALIZING("finalizing");

        private final String display;

        JobPhase(String display) {
            this.display = display;
        }
    }

    private static final class PlacementJob {
        private final CommandSourceStack source;
        private final BlockPos requestedPosition;
        private final boolean ruined;
        private JobPhase phase = JobPhase.PREPARING;
        private Structure structure;
        private ChunkGenerator generator;
        private StructureStart start;
        private TerrainPlan terrain;
        private BoundingBox bounds;
        private List<ChunkPos> chunks = List.of();
        private int terrainColumnsDone;
        private int terrainColumnsTotal;
        private int chunkIndex;
        private int decayX;
        private int decayY;
        private int decayZ;
        private int decayBlocksDone;
        private int decayBlocksTotal;
        private int changedTerrainBlocks;
        private int changedDecayBlocks;
        private boolean mutatedWorld;
        private boolean finished;

        private PlacementJob(
                CommandSourceStack source, BlockPos requestedPosition, boolean ruined
        ) {
            this.source = source;
            this.requestedPosition = requestedPosition.immutable();
            this.ruined = ruined;
        }

        private void tick(ServerLevel level) {
            long deadline = System.nanoTime() + JOB_BUDGET_NANOS;
            while (!this.finished && System.nanoTime() < deadline) {
                switch (this.phase) {
                    case PREPARING -> {
                        prepare(level);
                        // Validation is read-only. Start world changes on the following tick so an
                        // operator has a genuine safe-cancellation window.
                        return;
                    }
                    case TERRAIN -> processTerrainColumn(level);
                    case STRUCTURES -> {
                        processStructureChunk(level);
                        // A single structure chunk can itself consume most of the tick budget.
                        return;
                    }
                    case DECAY -> processDecayBlock(level);
                    case FINALIZING -> finalizePlacement(level);
                }
            }
        }

        private void prepare(ServerLevel level) {
            GeneratedHub generated = generate(level, this.requestedPosition);
            if (!generated.success()) {
                fail(generated.message());
                return;
            }
            this.structure = generated.structure();
            this.generator = generated.generator();
            this.start = generated.start();
            if (this.ruined) {
                List<StructurePiece> selected =
                        selectRuinedPieces(this.start.getPieces(), this.requestedPosition);
                this.start = new StructureStart(
                        this.structure,
                        this.start.getChunkPos(),
                        0,
                        new PiecesContainer(selected));
            }
            this.bounds = this.start.getBoundingBox();
            Optional<String> validationFailure = validateBounds(level, this.start, this.bounds);
            if (validationFailure.isPresent()) {
                fail(validationFailure.get());
                return;
            }
            this.terrain = TerrainPlan.from(this.start);
            this.terrainColumnsTotal = this.terrain.affectedColumns().size();

            ChunkPos min = new ChunkPos(this.bounds.minX() >> 4, this.bounds.minZ() >> 4);
            ChunkPos max = new ChunkPos(this.bounds.maxX() >> 4, this.bounds.maxZ() >> 4);
            this.chunks = ChunkPos.rangeClosed(min, max).toList();
            this.phase = JobPhase.TERRAIN;
        }

        private void processTerrainColumn(ServerLevel level) {
            this.mutatedWorld = true;
            long column = this.terrain.affectedColumns().get(this.terrainColumnsDone);
            this.changedTerrainBlocks += fitTerrainColumn(
                    level, this.terrain, ChunkPos.getX(column), ChunkPos.getZ(column));
            this.terrainColumnsDone++;
            if (this.terrainColumnsDone >= this.terrainColumnsTotal) {
                this.phase = JobPhase.STRUCTURES;
            }
        }

        private void processStructureChunk(ServerLevel level) {
            this.mutatedWorld = true;
            ChunkPos chunk = this.chunks.get(this.chunkIndex);
            this.start.placeInChunk(
                    level,
                    level.structureManager(),
                    this.generator,
                    level.getRandom(),
                    new BoundingBox(
                            chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(),
                            chunk.getMaxBlockX(), level.getMaxBuildHeight(), chunk.getMaxBlockZ()),
                    chunk);
            this.chunkIndex++;
            if (this.chunkIndex >= this.chunks.size()) {
                if (this.ruined) {
                    this.decayX = this.bounds.minX();
                    this.decayY = this.bounds.minY();
                    this.decayZ = this.bounds.minZ();
                    this.decayBlocksTotal =
                            (this.bounds.maxX() - this.bounds.minX() + 1)
                                    * (this.bounds.maxY() - this.bounds.minY() + 1)
                                    * (this.bounds.maxZ() - this.bounds.minZ() + 1);
                    this.phase = JobPhase.DECAY;
                } else {
                    this.phase = JobPhase.FINALIZING;
                }
            }
        }

        private void processDecayBlock(ServerLevel level) {
            BlockPos pos = new BlockPos(this.decayX, this.decayY, this.decayZ);
            this.changedDecayBlocks += weatherRuinBlock(level, this.bounds, pos, level.getSeed());
            this.decayBlocksDone++;
            this.decayZ++;
            if (this.decayZ > this.bounds.maxZ()) {
                this.decayZ = this.bounds.minZ();
                this.decayY++;
            }
            if (this.decayY > this.bounds.maxY()) {
                this.decayY = this.bounds.minY();
                this.decayX++;
            }
            if (this.decayX > this.bounds.maxX()) {
                this.phase = JobPhase.FINALIZING;
            }
        }

        private void finalizePlacement(ServerLevel level) {
            if (this.ruined) {
                complete(Component.literal(
                        "Placed an uninhabited ruined frontier hub at "
                                + this.bounds.getCenter().toShortString() + " over multiple ticks."));
                return;
            }
            Settlement settlement = SettlementConverter.convert(level, this.start, "frontier_hub");
            complete(Component.literal(
                    "Placed and terrain-fitted " + settlement.name() + " at "
                            + settlement.center().toShortString() + " over multiple ticks."));
        }

        private JobStatus status() {
            int percent = switch (this.phase) {
                case PREPARING -> 0;
                case TERRAIN -> progress(5, 65, this.terrainColumnsDone, this.terrainColumnsTotal);
                case STRUCTURES -> progress(65, 90, this.chunkIndex, this.chunks.size());
                case DECAY -> progress(90, 99, this.decayBlocksDone, this.decayBlocksTotal);
                case FINALIZING -> 99;
            };
            return new JobStatus(this.phase.display, percent, this.ruined, this.mutatedWorld);
        }

        private static int progress(int start, int end, int done, int total) {
            if (total <= 0) {
                return start;
            }
            return start + (int) ((long) (end - start) * Math.min(done, total) / total);
        }

        private void complete(Component message) {
            this.source.sendSuccess(() -> message, true);
            this.finished = true;
        }

        private void fail(String message) {
            this.source.sendFailure(Component.literal("Frontier hub placement failed: " + message));
            this.finished = true;
        }
    }

    private static long columnKey(int x, int z) {
        return ChunkPos.asLong(x, z);
    }

    private record TerrainPlan(
            BoundingBox bounds,
            Map<Long, ColumnPlan> coreColumns,
            Map<Long, ColumnTarget> nearestTargets,
            List<Long> affectedColumns
    ) {
        static TerrainPlan from(StructureStart start) {
            Map<Long, ColumnPlan> columns = new HashMap<>();
            for (StructurePiece piece : start.getPieces()) {
                if (!(piece instanceof PoolElementStructurePiece poolPiece)) {
                    continue;
                }
                BoundingBox box = piece.getBoundingBox();
                int surfaceY = box.minY() + poolPiece.getGroundLevelDelta() - 1;
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        long key = columnKey(x, z);
                        ColumnPlan incoming = new ColumnPlan(surfaceY, box.maxY());
                        columns.merge(key, incoming, (left, right) ->
                                new ColumnPlan(Math.min(left.surfaceY(), right.surfaceY()),
                                        Math.max(left.clearToY(), right.clearToY())));
                    }
                }
            }
            Map<Long, ColumnTarget> nearest = buildNearestTargets(columns, BLEND_RADIUS);
            List<Long> affected = new ArrayList<>(nearest.keySet());
            affected.sort(Comparator
                    .comparingInt((Long key) -> ChunkPos.getX(key))
                    .thenComparingInt(ChunkPos::getZ));
            return new TerrainPlan(start.getBoundingBox(), Map.copyOf(columns),
                    nearest, List.copyOf(affected));
        }
    }

    /**
     * Builds the nearest terrain target once. The previous implementation performed the same
     * radius search independently for every terrain column, producing tens of millions of map
     * lookups for a large hub.
     */
    private static Map<Long, ColumnTarget> buildNearestTargets(
            Map<Long, ColumnPlan> core, int radius
    ) {
        Map<Long, NearestColumn> nearest = new HashMap<>();
        int radiusSq = radius * radius;
        for (Map.Entry<Long, ColumnPlan> entry : core.entrySet()) {
            int originX = ChunkPos.getX(entry.getKey());
            int originZ = ChunkPos.getZ(entry.getKey());
            int surfaceY = entry.getValue().surfaceY();
            nearest.put(entry.getKey(), new NearestColumn(surfaceY, 0, originX, originZ));
            // Interior footprint columns can never be the nearest source for a point outside the
            // footprint. Expanding only its edge avoids millions of duplicate candidate writes.
            if (!isCoreBoundary(core, originX, originZ)) {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distanceSq = dx * dx + dz * dz;
                    if (distanceSq == 0 || distanceSq > radiusSq) {
                        continue;
                    }
                    long key = columnKey(originX + dx, originZ + dz);
                    NearestColumn incoming = new NearestColumn(surfaceY, distanceSq, originX, originZ);
                    nearest.merge(key, incoming, FrontierHubRuntimePlacer::nearerColumn);
                }
            }
        }
        Map<Long, ColumnTarget> targets = new HashMap<>(nearest.size());
        nearest.forEach((key, value) ->
                targets.put(key, new ColumnTarget(value.surfaceY(), Math.sqrt(value.distanceSq()))));
        return Map.copyOf(targets);
    }

    private static boolean isCoreBoundary(Map<Long, ColumnPlan> core, int x, int z) {
        return !core.containsKey(columnKey(x - 1, z))
                || !core.containsKey(columnKey(x + 1, z))
                || !core.containsKey(columnKey(x, z - 1))
                || !core.containsKey(columnKey(x, z + 1));
    }

    private static NearestColumn nearerColumn(NearestColumn left, NearestColumn right) {
        if (left.distanceSq() != right.distanceSq()) {
            return left.distanceSq() < right.distanceSq() ? left : right;
        }
        if (left.originX() != right.originX()) {
            return left.originX() < right.originX() ? left : right;
        }
        return left.originZ() <= right.originZ() ? left : right;
    }

    private record ColumnPlan(int surfaceY, int clearToY) {
    }

    private record ColumnTarget(int surfaceY, double distance) {
    }

    private record NearestColumn(int surfaceY, int distanceSq, int originX, int originZ) {
    }

    public record PlacementResult(boolean success, String message, Settlement settlement,
                                  int pieces, int changedBlocks) {
        static PlacementResult success(Settlement settlement, int pieces, int changedBlocks) {
            return new PlacementResult(true, "", settlement, pieces, changedBlocks);
        }

        static PlacementResult failure(String message) {
            return new PlacementResult(false, message, null, 0, 0);
        }
    }

    private record GeneratedHub(boolean success, String message, Structure structure,
                                ChunkGenerator generator, StructureStart start) {
        static GeneratedHub success(Structure structure, ChunkGenerator generator, StructureStart start) {
            return new GeneratedHub(true, "", structure, generator, start);
        }

        static GeneratedHub failure(String message) {
            return new GeneratedHub(false, message, null, null, null);
        }
    }

    public record RuinPlacementResult(boolean success, String message, BoundingBox bounds,
                                      int pieces, int completePieces, int changedTerrainBlocks,
                                      int changedDecayBlocks) {
        static RuinPlacementResult success(BoundingBox bounds, int pieces, int completePieces,
                                            int terrainChanges, int decayChanges) {
            return new RuinPlacementResult(true, "", bounds, pieces, completePieces,
                    terrainChanges, decayChanges);
        }

        static RuinPlacementResult failure(String message) {
            return new RuinPlacementResult(false, message, null, 0, 0, 0, 0);
        }
    }

    public record StartResult(boolean success, String message) {
        static StartResult accepted() {
            return new StartResult(true, "");
        }

        static StartResult rejected(String message) {
            return new StartResult(false, message);
        }
    }

    public record JobStatus(String phase, int percent, boolean ruined, boolean worldChanged) {
    }

    public record CancelResult(boolean success, String message) {
        static CancelResult cancelled() {
            return new CancelResult(true, "");
        }

        static CancelResult rejected(String message) {
            return new CancelResult(false, message);
        }
    }
}
