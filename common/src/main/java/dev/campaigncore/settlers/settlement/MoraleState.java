package dev.campaigncore.settlers.settlement;

import net.minecraft.util.StringRepresentable;

/// Derived social posture. Morale is intentionally not saved: it always reflects current conditions
/// and recent persisted chronicle events rather than becoming another stale meter.
public enum MoraleState implements StringRepresentable {
    THRIVING("thriving"),
    STEADY("steady"),
    DISTRESSED("distressed");

    private final String name;

    MoraleState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    public boolean allowsOptionalConversation() {
        return this != DISTRESSED;
    }

    public boolean spendsBreakAtHome(boolean personalPreference) {
        return this == DISTRESSED || (this == STEADY && personalPreference);
    }

    public boolean takesOptionalErrands() {
        return this != DISTRESSED;
    }
}
