package dev.campaigncore.campaign;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record CampaignValue(Type type, Object value) {
    public enum Type { BOOLEAN, INTEGER, DOUBLE, STRING, RESOURCE_LOCATION, BLOCK_POS, UUID }

    public CampaignValue {
        if (type == null || value == null) throw new IllegalArgumentException("Campaign values cannot be null");
    }

    public static CampaignValue of(boolean value) { return new CampaignValue(Type.BOOLEAN, value); }
    public static CampaignValue of(int value) { return new CampaignValue(Type.INTEGER, value); }
    public static CampaignValue of(double value) { return new CampaignValue(Type.DOUBLE, value); }
    public static CampaignValue of(String value) { return new CampaignValue(Type.STRING, value); }
    public static CampaignValue of(ResourceLocation value) { return new CampaignValue(Type.RESOURCE_LOCATION, value); }
    public static CampaignValue of(BlockPos value) { return new CampaignValue(Type.BLOCK_POS, value.immutable()); }
    public static CampaignValue of(UUID value) { return new CampaignValue(Type.UUID, value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        switch (type) {
            case BOOLEAN -> tag.putBoolean("value", (Boolean)value);
            case INTEGER -> tag.putInt("value", (Integer)value);
            case DOUBLE -> tag.putDouble("value", (Double)value);
            case STRING -> tag.putString("value", (String)value);
            case RESOURCE_LOCATION -> tag.putString("value", value.toString());
            case BLOCK_POS -> tag.putLong("value", ((BlockPos)value).asLong());
            case UUID -> tag.putUUID("value", (UUID)value);
        }
        return tag;
    }

    public static CampaignValue load(CompoundTag tag) {
        Type type = Type.valueOf(tag.getString("type"));
        return switch (type) {
            case BOOLEAN -> of(tag.getBoolean("value"));
            case INTEGER -> of(tag.getInt("value"));
            case DOUBLE -> of(tag.getDouble("value"));
            case STRING -> of(tag.getString("value"));
            case RESOURCE_LOCATION -> of(ResourceLocation.parse(tag.getString("value")));
            case BLOCK_POS -> of(BlockPos.of(tag.getLong("value")));
            case UUID -> of(tag.getUUID("value"));
        };
    }
}
