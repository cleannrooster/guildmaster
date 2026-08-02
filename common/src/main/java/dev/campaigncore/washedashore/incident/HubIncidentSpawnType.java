package dev.campaigncore.washedashore.incident;

import net.minecraft.util.StringRepresentable;

public enum HubIncidentSpawnType implements StringRepresentable {
    AROUND_HUB("around_hub"),APPROACHING_HUB("approaching_hub"),MOVING_PATROL("moving_patrol");
    public static final StringRepresentable.EnumCodec<HubIncidentSpawnType> CODEC=StringRepresentable.fromEnum(HubIncidentSpawnType::values);
    private final String name;
    HubIncidentSpawnType(String name){this.name=name;}
    @Override public String getSerializedName(){return name;}
}
