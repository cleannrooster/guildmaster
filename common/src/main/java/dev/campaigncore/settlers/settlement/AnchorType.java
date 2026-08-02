package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/// The anchor vocabulary connecting spaces to behaviors. Anchors are generated from block scans of
/// classified vanilla structures in the beta, but the vocabulary is deliberately structure-agnostic:
/// hand-authored structures will later register these same types via structure metadata.
public enum AnchorType implements StringRepresentable {
    HOME("home"),
    SLEEP("sleep"),
    WORK("work"),
    STORAGE("storage"),
    CARRY_SOURCE("carry_source"),
    CARRY_DESTINATION("carry_destination"),
    GUARD_POST("guard_post"),
    PATROL("patrol"),
    PLAZA("plaza"),
    SOCIAL("social"),
    MEAL("meal"),
    SHRINE("shrine"),
    MAP_TABLE("map_table"),
    ANIMAL_TENDING("animal_tending"),
    CROP_TENDING("crop_tending"),
    WAGON_REPAIR("wagon_repair"),
    AUTHORITY("authority"),
    ARRIVAL("arrival"),
    TRAVELER_LODGING("traveler_lodging"),
    EMERGENCY_SHELTER("emergency_shelter"),
    MUSTER("muster"),
    INFIRMARY("infirmary"),
    WATER("water"),
    FISHING("fishing");

    public static final StringRepresentable.EnumCodec<AnchorType> CODEC =
            StringRepresentable.fromEnum(AnchorType::values);

    private final String name;

    /// Resolves an anchor type from its serialized name, or null if none matches. Used by manual-NBT
    /// deserialization (see {@link dev.campaigncore.settlers.entity.SettlerEntity}) where a codec isn't convenient.
    public static AnchorType byName(String serializedName) {
        return CODEC.byName(serializedName);
    }

    AnchorType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
