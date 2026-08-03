package dev.campaigncore.network;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** One objective position plus the placeholder icon used to identify its purpose. */
public record ObjectiveMarker(ResourceLocation id,BlockPos position,Type type,boolean incident) {
    public enum Type { GUIDE, UNDERTAKER, SETTLEMENT, RAVEN, DARK_FOREST, DEVILS_CROSSING, RAID , SECOND_SETTLEMENT}
}
