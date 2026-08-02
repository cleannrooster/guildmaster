package dev.campaigncore.settlers.behavior;

/// Pure policy for how long any settler may retain a hostile player as its target.
public final class SettlerPlayerHostility {
    public static final int HOSTILITY_TICKS = 8 * 20;

    private SettlerPlayerHostility() {
    }

    public static boolean shouldContinue(boolean insideSettlement, int ticksSinceHostileAction) {
        return insideSettlement
                && ticksSinceHostileAction >= 0
                && ticksSinceHostileAction < HOSTILITY_TICKS;
    }
}
