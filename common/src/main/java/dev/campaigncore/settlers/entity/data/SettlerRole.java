package dev.campaigncore.settlers.entity.data;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/// Broad population category of a Settler. Fine-grained identity (profession, faction, schedule,
/// equipment) lives in the {@link SettlerProfile} data; the role is the coarse bucket that behavior
/// and threat-response logic switch on (civilians shelter, guards muster, travelers leave).
public enum SettlerRole implements StringRepresentable {
    CIVILIAN("civilian"),
    WORKER("worker"),
    AUTHORITY("authority"),
    GUARD("guard"),
    TRAVELER("traveler");

    public static final Codec<SettlerRole> CODEC = StringRepresentable.fromEnum(SettlerRole::values);

    private final String name;

    SettlerRole(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
