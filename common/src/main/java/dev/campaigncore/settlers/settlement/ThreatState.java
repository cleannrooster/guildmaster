package dev.campaigncore.settlers.settlement;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/// Settlement threat posture. Settlers read this from their settlement each schedule evaluation;
/// nothing writes it except the settlement's own threat logic (and the dev command).
public enum ThreatState implements StringRepresentable {
    NORMAL("normal"),
    ALERT("alert"),
    UNDER_ATTACK("under_attack"),
    RECOVERY("recovery"),
    // Beta: reachable only via command; abandonment mechanics come after the vertical slice.
    ABANDONED("abandoned"),
    PANIC("panic");

    public static final Codec<ThreatState> CODEC = StringRepresentable.fromEnum(ThreatState::values);

    private final String name;

    ThreatState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
