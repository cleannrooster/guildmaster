package dev.campaigncore.settlers.detection;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import dev.campaigncore.settlers.settlement.StructureAssignment;

import java.util.HashSet;
import java.util.Set;

/// Performs a read-only census of settlement-relevant infrastructure inside
/// a generated structure start.
///
/// This class does not modify blocks, generate anchors, classify final
/// structure roles, or spawn entities.
public final class InfrastructureScanner {
    private InfrastructureScanner() {
    }

    public static InfrastructureScan scan(
            ServerLevel level,
            StructureStart start
    ) {
        MutableCounts counts = new MutableCounts();
        int totalFarmTiers = 0;

        /*
         * Jigsaw pieces can overlap. Without deduplication, blocks inside an
         * overlap would be counted once for each piece.
         */
        Set<BlockPos> visited = new HashSet<>();

        for (StructurePiece piece : start.getPieces()) {
            scanBounds(
                    level,
                    StructureAssignment.inflatedScanBounds(piece.getBoundingBox()),
                    visited,
                    counts
            );
        }

        return new InfrastructureScan(
                start.getPieces().size(),
                counts.beds,
                counts.farmland,
                counts.plantedCrops,
                counts.composters,
                counts.hayBales,
                counts.workstations,
                counts.bells
        );
    }

    private static void scanBounds(
            ServerLevel level,
            BoundingBox bounds,
            Set<BlockPos> visited,
            MutableCounts counts
    ) {
        BlockPos.MutableBlockPos cursor =
                new BlockPos.MutableBlockPos();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                    cursor.set(x, y, z);

                    BlockPos immutable = cursor.immutable();

                    if (!visited.add(immutable)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    countBlock(level, immutable, state, counts);
                }
            }
        }
    }

    private static void countBlock(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            MutableCounts counts
    ) {
        Block block = state.getBlock();

        if (block instanceof BedBlock
                && state.getValue(BedBlock.PART) == BedPart.HEAD) {
            counts.beds++;
        }

        if (block == Blocks.FARMLAND) {
            counts.farmland++;
        }

        if (state.is(BlockTags.CROPS) && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            counts.plantedCrops++;
        }

        if (block == Blocks.COMPOSTER) {
            counts.composters++;
        }

        if (block == Blocks.HAY_BLOCK) {
            counts.hayBales++;
        }

        if (block == Blocks.BELL) {
            counts.bells++;
        }

        if (isRecognizedWorkstation(state)) {
            counts.workstations++;
        }
    }

    private static boolean isRecognizedWorkstation(
            BlockState state
    ) {
        Block block = state.getBlock();

        return block == Blocks.BLAST_FURNACE
                || block == Blocks.SMOKER
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.BREWING_STAND
                || block == Blocks.COMPOSTER
                || block == Blocks.BARREL
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.LECTERN
                || block == Blocks.LOOM
                || block == Blocks.STONECUTTER
                || block == Blocks.GRINDSTONE
                || block == Blocks.SMITHING_TABLE;
    }

    private static final class MutableCounts {
        private int beds;
        private int farmland;
        private int plantedCrops;
        private int composters;
        private int hayBales;
        private int workstations;
        private int bells;
    }
}
