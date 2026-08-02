package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/// Settlement-level function assigned to a structure. The internal API operates on these abstract
/// roles, never on vanilla template identity — template names only appear in the datapack mapping
/// rules that feed the classifier.
public enum StructureRole implements StringRepresentable {
    RESIDENCE("residence"),
    AUTHORITY("authority"),
    GUARD_POST("guard_post"),
    INN("inn"),
    TRAVELER_LODGE("traveler_lodge"),
    STABLE("stable"),
    TRADER("trader"),
    WORKSHOP("workshop"),
    PROCESSING("processing"),
    SHRINE("shrine"),
    INFIRMARY("infirmary"),
    STORAGE("storage"),
    GRANARY("granary"),
    COMMUNAL_DINING("communal_dining"),
    MEETING_HALL("meeting_hall"),
    RECORDS("records"),
    ARMORY("armory"),
    CULTURAL("cultural"),
    FARM("farm"),
    PASTURE("pasture"),
    PLAZA("plaza"),
    ROAD("road"),
    WATER("water"),
    AUXILIARY("auxiliary");

    public static final Codec<StructureRole> CODEC = StringRepresentable.fromEnum(StructureRole::values);

    private final String name;

    StructureRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
