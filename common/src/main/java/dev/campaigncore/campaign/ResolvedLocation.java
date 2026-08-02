package dev.campaigncore.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record ResolvedLocation(ResourceLocation id, ResourceLocation dimension, BlockPos position) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id.toString());
        tag.putString("dimension", dimension.toString());
        tag.putLong("position", position.asLong());
        return tag;
    }
    public static ResolvedLocation load(CompoundTag tag) {
        return new ResolvedLocation(ResourceLocation.parse(tag.getString("id")),
                ResourceLocation.parse(tag.getString("dimension")), BlockPos.of(tag.getLong("position")));
    }
}
