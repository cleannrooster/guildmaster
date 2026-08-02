package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// One-time block scan at conversion that turns classified structures into the settlement's
/// {@link AnchorTable}. Runtime audits rebuild this table atomically through SettlementAnchorUpdater.
///
/// Beta shortcut (documented): structures whose chunks aren't loaded when the player triggers
/// conversion are skipped (logged) — their block anchors are simply absent. Role-derived anchors
/// (centers) are still generated since they need no block access.
public final class AnchorGenerator {
    private AnchorGenerator() {
    }

    public static boolean canScanAll(ServerLevel level, List<StructureAssignment> structures) {
        return structures.stream().allMatch(structure -> {
            BoundingBox box = structure.scanBounds();
            return level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ());
        });
    }

    public static AnchorTable generate(ServerLevel level, List<StructureAssignment> structures, BlockPos settlementCenter) {
        AnchorTable anchors = new AnchorTable();
        int skipped = 0;

        for (StructureAssignment structure : structures) {
            addRoleAnchors(level, anchors, structure);
            if (structure.role() == StructureRole.ROAD || structure.role() == StructureRole.AUXILIARY) {
                continue;
            }
            BoundingBox box = structure.scanBounds();
            if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
                skipped++;
                continue;
            }
            scanBlocks(level, anchors, structure);
        }

        addRoadAnchors(level, anchors, structures, settlementCenter);

        if (skipped > 0) {
            SettlersMod.LOGGER.warn("Anchor scan skipped {} structures in unloaded chunks; their block anchors are missing.", skipped);
        }
        return anchors;
    }

    /// Anchors implied by a structure's role. Deliberately stands outside the building — in front of
    /// a door if one is found, otherwise along the building's own footprint edge — rather than at the
    /// bounding box's raw geometric center: a heightmap lookup there lands on the roof for any roofed
    /// structure, which is exactly the "guards standing on the roof" failure this avoids.
    private static void addRoleAnchors(ServerLevel level, AnchorTable anchors, StructureAssignment structure) {
        BlockPos center = findExteriorAnchor(level, structure);
        switch (structure.role()) {
            case AUTHORITY, MEETING_HALL -> {
                anchors.add(AnchorType.AUTHORITY, center);
                anchors.add(AnchorType.EMERGENCY_SHELTER, center);
            }
            case SHRINE -> anchors.add(AnchorType.SHRINE, center);
            case GUARD_POST, ARMORY -> anchors.add(AnchorType.GUARD_POST, center);
            case INN, TRAVELER_LODGE -> anchors.add(AnchorType.TRAVELER_LODGING, center);
            case STABLE -> anchors.add(AnchorType.ANIMAL_TENDING, center);
            case PASTURE -> anchors.add(AnchorType.ANIMAL_TENDING, center);
            case STORAGE, GRANARY -> anchors.add(AnchorType.CARRY_SOURCE, center);
            case WORKSHOP, PROCESSING, TRADER -> anchors.add(AnchorType.CARRY_DESTINATION, center);
            case INFIRMARY -> anchors.add(AnchorType.INFIRMARY, center);
            case RECORDS -> anchors.add(AnchorType.MAP_TABLE, center);
            case PLAZA -> {
                anchors.add(AnchorType.PLAZA, center);
                anchors.add(AnchorType.SOCIAL, center);
                anchors.add(AnchorType.MUSTER, center);
            }
            case RESIDENCE -> anchors.add(AnchorType.HOME, center);
            case COMMUNAL_DINING -> anchors.add(AnchorType.MEAL, center);
            case WATER -> anchors.add(AnchorType.WATER, center);
            default -> {
            }
        }
    }

    /// Ground-level exterior position for a "stand near this structure" anchor: prefers just outside a
    /// ground-floor door, then the lowest walkable spot around or inside the footprint. Deliberately
    /// never uses a highest-surface heightmap: at a building's X/Z that surface is usually its roof.
    private static BlockPos findExteriorAnchor(ServerLevel level, StructureAssignment structure) {
        // Role positioning stays tied to the physical footprint; only block detection is inflated.
        BoundingBox box = structure.bounds();
        if (!level.hasChunksAt(box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return structure.center();
        }
        return findDoorStandPosition(level, box)
                .or(() -> findPerimeterStandPosition(level, box))
                .or(() -> findFootprintStandPosition(level, box))
                .orElse(structure.center());
    }

    /// Finds a ground-floor door and returns a standable position just outside it, on the exterior
    /// side of the structure's footprint.
    private static Optional<BlockPos> findDoorStandPosition(ServerLevel level, BoundingBox box) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (!(state.getBlock() instanceof DoorBlock) || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                        continue;
                    }
                    BlockPos door = cursor.immutable();
                    for (Direction direction : Direction.Plane.HORIZONTAL) {
                        BlockPos outside = door.relative(direction);
                        if (isOutsideFootprint(box, outside) && isStandable(level, outside)) {
                            return Optional.of(outside);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /// No door found (or it's boxed in): walk the ring just outside the footprint, lowest Y first.
    /// Template bounds often include buried foundations, so checking only minY incorrectly misses the
    /// street level and previously forced the roof-selecting heightmap fallback.
    private static Optional<BlockPos> findPerimeterStandPosition(ServerLevel level, BoundingBox box) {
        for (int y = searchMinY(level, box); y <= searchMaxY(level, box); y++) {
            for (int x = box.minX() - 1; x <= box.maxX() + 1; x++) {
                BlockPos north = new BlockPos(x, y, box.minZ() - 1);
                if (isStandable(level, north)) {
                    return Optional.of(north);
                }
                BlockPos south = new BlockPos(x, y, box.maxZ() + 1);
                if (isStandable(level, south)) {
                    return Optional.of(south);
                }
            }
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos west = new BlockPos(box.minX() - 1, y, z);
                if (isStandable(level, west)) {
                    return Optional.of(west);
                }
                BlockPos east = new BlockPos(box.maxX() + 1, y, z);
                if (isStandable(level, east)) {
                    return Optional.of(east);
                }
            }
        }
        return Optional.empty();
    }

    /// Last loaded-structure fallback: a real two-block-tall walking space inside the footprint,
    /// again preferring the lowest floor so upper floors and roofs cannot win merely by being higher.
    private static Optional<BlockPos> findFootprintStandPosition(ServerLevel level, BoundingBox box) {
        for (int y = searchMinY(level, box); y <= searchMaxY(level, box); y++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (isStandable(level, pos)) {
                        return Optional.of(pos);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static int searchMinY(ServerLevel level, BoundingBox box) {
        return Math.max(level.getMinBuildHeight() + 1, box.minY() - 2);
    }

    private static int searchMaxY(ServerLevel level, BoundingBox box) {
        return Math.min(level.getMaxBuildHeight() - 2, box.maxY() + 1);
    }

    private static boolean isOutsideFootprint(BoundingBox box, BlockPos pos) {
        return pos.getX() < box.minX() || pos.getX() > box.maxX() || pos.getZ() < box.minZ() || pos.getZ() > box.maxZ();
    }

    /// A one-block-tall gap with solid footing: not a roof, not thin air. Package-private: reused by
    /// Used by conversion-time placement helpers to find a safe spot for generated props.
    static boolean isStandable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        BlockState at = level.getBlockState(pos);
        BlockState above = level.getBlockState(pos.above());
        return !below.getCollisionShape(level, pos.below()).isEmpty()
                && at.getCollisionShape(level, pos).isEmpty()
                && above.getCollisionShape(level, pos.above()).isEmpty();
    }

    /// Block census of a structure's interior for position-precise anchors.
    private static void scanBlocks(ServerLevel level, AnchorTable anchors, StructureAssignment structure) {
        BoundingBox box = structure.scanBounds();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    BlockPos pos = cursor.immutable();

                    if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                        addCapped(anchors, AnchorType.SLEEP, pos);
                    } else if (state.is(Blocks.BELL)) {
                        anchors.add(AnchorType.PLAZA, pos);
                        anchors.add(AnchorType.MUSTER, pos);
                    } else if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL)) {
                        addCapped(anchors, AnchorType.STORAGE, pos);
                        if (state.is(Blocks.BARREL)) {
                            // Any barrel doubles as a fishing workstation (design decision: precision
                            // would need per-structure profession hints on the classifier, which is
                            // more machinery than the beta needs right now).
                            addCapped(anchors, AnchorType.FISHING, pos);
                        }
                    } else if (state.is(Blocks.FARMLAND)) {
                        addCapped(anchors, AnchorType.CROP_TENDING, pos.above());
                    } else if (state.is(Blocks.COMPOSTER)) {
                        addCapped(anchors, AnchorType.CROP_TENDING, pos);
                    } else if (state.is(Blocks.SMITHING_TABLE) || state.is(Blocks.ANVIL) || state.is(Blocks.GRINDSTONE)
                            || state.is(Blocks.STONECUTTER) || state.is(Blocks.FLETCHING_TABLE) || state.is(Blocks.LOOM)
                            || state.is(Blocks.CARTOGRAPHY_TABLE)) {
                        addCapped(anchors, AnchorType.WORK, pos);
                    } else if (state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER)
                            || state.is(Blocks.CAMPFIRE)) {
                        addCapped(anchors, AnchorType.MEAL, pos);
                    } else if (state.is(Blocks.LECTERN)) {
                        addCapped(anchors, AnchorType.MAP_TABLE, pos);
                    } else if (state.is(Blocks.BREWING_STAND)) {
                        anchors.add(AnchorType.INFIRMARY, pos);
                    } else if (state.is(Blocks.WATER) && structure.role() != StructureRole.FARM) {
                        // Wells and troughs; farms are excluded so irrigation channels don't flood the table.
                        addCapped(anchors, AnchorType.WATER, pos);
                    }
                }
            }
        }
    }
    public static void addForStructures(
            ServerLevel level,
            AnchorTable anchors,
            List<StructureAssignment> structures
    ) {
        int skipped = 0;

        for (StructureAssignment structure : structures) {
            addRoleAnchors(level, anchors, structure);

            if (structure.role() == StructureRole.ROAD
                    || structure.role()
                    == StructureRole.AUXILIARY) {
                continue;
            }

            BoundingBox box = structure.scanBounds();

            if (!level.hasChunksAt(
                    box.minX(),
                    box.minZ(),
                    box.maxX(),
                    box.maxZ()
            )) {
                skipped++;
                continue;
            }

            scanBlocks(level, anchors, structure);
        }

        if (skipped > 0) {
            SettlersMod.LOGGER.warn(
                    "Incremental anchor scan skipped {} structures "
                            + "in unloaded chunks.",
                    skipped
            );
        }
    }
    /// Roads produce the settlement's movement grammar: an ARRIVAL point at the edge (where travelers
    /// enter) and a PATROL loop over street positions ordered around the center.
    private static void addRoadAnchors(ServerLevel level, AnchorTable anchors, List<StructureAssignment> structures, BlockPos center) {
        List<BlockPos> roadPoints = new ArrayList<>();
        for (StructureAssignment structure : structures) {
            if (structure.role() == StructureRole.ROAD) {
                BoundingBox box = structure.bounds();
                roadPoints.add(findFootprintStandPosition(level, box)
                        .or(() -> findPerimeterStandPosition(level, box))
                        .orElse(structure.center()));
            }
        }
        if (roadPoints.isEmpty()) {
            SettlersMod.LOGGER.warn("Settlement at {} has no classified road pieces; ARRIVAL/PATROL anchors fall back to the center.", center);
            anchors.add(AnchorType.ARRIVAL, center);
            anchors.add(AnchorType.PATROL, center);
            return;
        }

        roadPoints.stream()
                .max(Comparator.comparingDouble(pos -> pos.distSqr(center)))
                .ifPresent(pos -> anchors.add(AnchorType.ARRIVAL, pos));

        // Patrol loop: up to 8 street points, ordered by angle around the center so the route reads as
        // a perimeter walk instead of random darting. Subsampled evenly from the angle-sorted list —
        // truncating it instead would keep only the 8 smallest angles, i.e. one angular wedge of town,
        // and the "perimeter" walk would cover a single side.
        List<BlockPos> byAngle = roadPoints.stream()
                .sorted(Comparator.comparingDouble(pos ->
                        Math.atan2(pos.getZ() - center.getZ(), pos.getX() - center.getX())))
                .toList();
        int stops = Math.min(8, byAngle.size());
        for (int i = 0; i < stops; i++) {
            anchors.add(AnchorType.PATROL, byAngle.get(i * byAngle.size() / stops));
        }
    }

    private static void addCapped(AnchorTable anchors, AnchorType type, BlockPos pos) {
        // Historical versions capped each type at 24 settlement-wide. That made structures appended
        // by expansion invisible whenever the original village filled the cap first. Labor demand has
        // its own per-chunk limits in WorkstationCounter, so the authoritative spatial table should
        // retain every real anchor.
        anchors.add(type, pos);
    }

}
