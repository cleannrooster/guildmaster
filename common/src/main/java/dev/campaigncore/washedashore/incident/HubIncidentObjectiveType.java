package dev.campaigncore.washedashore.incident;

import net.minecraft.util.StringRepresentable;

public enum HubIncidentObjectiveType implements StringRepresentable {
    KILL_GROUP("kill_group"),KILL_LEADER("kill_leader"),DEFEND_LOCATION("defend_location"),BATTLE("battle");
    public static final StringRepresentable.EnumCodec<HubIncidentObjectiveType> CODEC=StringRepresentable.fromEnum(HubIncidentObjectiveType::values);
    private final String name;
    HubIncidentObjectiveType(String name){this.name=name;}
    @Override public String getSerializedName(){return name;}
}
