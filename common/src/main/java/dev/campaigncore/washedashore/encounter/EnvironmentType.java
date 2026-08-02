package dev.campaigncore.washedashore.encounter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;

/**
 * Coarse terrain categories used for simple environment weighting of encounter candidates (chiefly the
 * Devil's Crossing burrower: sandy favors worms, forest/cave favors spiders/arthropods, water favors
 * aquatic creatures, cold favors Frostmaw, generic favors ravager/hoglin or the native fallback).
 * {@link #GENERIC} always matches, so a candidate without an environment restriction is universally eligible.
 */
public enum EnvironmentType {
    SANDY,
    FOREST,
    CAVE,
    WATER,
    COLD,
    GENERIC;

    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }

    /** Best-effort classification match at {@code pos} using biome tags and surface probes. */
    public boolean matches(ServerLevel level, BlockPos pos) {
        var biome = level.getBiome(pos);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
        return switch (this) {
            case GENERIC -> true;
            case SANDY -> biome.is(BiomeTags.HAS_DESERT_PYRAMID) || biome.is(BiomeTags.IS_BADLANDS)
                    || biome.is(BiomeTags.IS_BEACH);
            case FOREST -> biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA)
                    || biome.is(BiomeTags.IS_JUNGLE);
            // A cave anchor sits well below the local surface (deep-dark / lush-cave biomes or buried).
            case CAVE -> pos.getY() < surface - 6;
            case WATER -> !level.getFluidState(pos).isEmpty()
                    || !level.getFluidState(BlockPos.containing(pos.getX(), surface - 1, pos.getZ())).isEmpty()
                    || biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER);
            case COLD -> biome.value().coldEnoughToSnow(pos);
        };
    }
}
