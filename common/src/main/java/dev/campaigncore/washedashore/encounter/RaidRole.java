package dev.campaigncore.washedashore.encounter;

import net.minecraft.util.StringRepresentable;

/**
 * Tactical role of a member within a raid composition. Metadata only: roles let raid templates and the
 * {@code combat_encounter status} command describe a roster's shape (one commander, several frontline/
 * ranged, a few support/specialist, an optional heavy). They do not change how a member is spawned.
 */
public enum RaidRole implements StringRepresentable {
    COMMANDER,
    FRONTLINE,
    RANGED,
    SUPPORT,
    SPECIALIST,
    HEAVY;

    public static final StringRepresentable.EnumCodec<RaidRole> CODEC = StringRepresentable.fromEnum(RaidRole::values);

    @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
}
