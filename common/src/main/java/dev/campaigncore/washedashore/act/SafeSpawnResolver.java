package dev.campaigncore.washedashore.act;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Optional;

/** Resolves feet positions after preparing the containing chunk. */
public final class SafeSpawnResolver {
    private SafeSpawnResolver() {}

    public static Optional<BlockPos> findSafeFeet(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        for (int offset = 0; offset <= 8; offset++) {
            BlockPos down = new BlockPos(x, surfaceY - offset, z);
            if (isSafeFeet(level, down)) return Optional.of(down);
            if (offset > 0) {
                BlockPos up = new BlockPos(x, surfaceY + offset, z);
                if (isSafeFeet(level, up)) return Optional.of(up);
            }
        }
        return Optional.empty();
    }

    public static Optional<BlockPos> findNearbySafeFeet(ServerLevel level, BlockPos center, int radius) {
        Optional<BlockPos> exact = findSafeFeet(level, center.getX(), center.getZ());
        if (exact.isPresent()) return exact;
        for (int ring = 2; ring <= radius; ring += 2) {
            for (int dx = -ring; dx <= ring; dx += 2) {
                Optional<BlockPos> result = findSafeFeet(level, center.getX() + dx, center.getZ() - ring);
                if (result.isPresent()) return result;
                result = findSafeFeet(level, center.getX() + dx, center.getZ() + ring);
                if (result.isPresent()) return result;
            }
            for (int dz = -ring + 2; dz < ring; dz += 2) {
                Optional<BlockPos> result = findSafeFeet(level, center.getX() - ring, center.getZ() + dz);
                if (result.isPresent()) return result;
                result = findSafeFeet(level, center.getX() + ring, center.getZ() + dz);
                if (result.isPresent()) return result;
            }
        }
        return Optional.empty();
    }

    public static boolean isSafeFeet(ServerLevel level, BlockPos feet) {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() >= level.getMaxBuildHeight() - 1) return false;
        BlockPos groundPos = feet.below();
        BlockState ground = level.getBlockState(groundPos);
        BlockState atFeet = level.getBlockState(feet);
        BlockState atHead = level.getBlockState(feet.above());
        if (!ground.isFaceSturdy(level, groundPos, Direction.UP)
                || !ground.getFluidState().isEmpty()
                || !atFeet.getFluidState().isEmpty()
                || !atHead.getFluidState().isEmpty()
                || !atFeet.getCollisionShape(level, feet).isEmpty()
                || !atHead.getCollisionShape(level, feet.above()).isEmpty()) return false;
        return !ground.is(BlockTags.LEAVES)
                && !ground.is(Blocks.MAGMA_BLOCK)
                && !ground.is(Blocks.CACTUS)
                && !ground.is(Blocks.CAMPFIRE)
                && !ground.is(Blocks.SOUL_CAMPFIRE)
                && !ground.is(Blocks.FIRE)
                && !ground.is(Blocks.SOUL_FIRE)
                && !ground.is(Blocks.POWDER_SNOW);
    }
}
