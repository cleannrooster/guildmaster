package dev.campaigncore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** One campaign destination and its place in the player-facing navigation hierarchy. */
public record ObjectiveMarker(ResourceLocation id,ResourceKey<Level> dimension,BlockPos position,Type type,Category category) {
    public enum Category { OBJECTIVE, OPPORTUNITY, LANDMARK }
    public enum Type { GUIDE, UNDERTAKER, SETTLEMENT, RAVEN, DARK_FOREST, DEVILS_CROSSING, RAID , SECOND_SETTLEMENT}
}
