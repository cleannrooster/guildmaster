package dev.campaigncore.settlers.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlerHurtByTargetGoalTest {
    @Test
    void settlerImmediatelyStopsTargetingPlayerOutsideSettlement() {
        assertFalse(SettlerPlayerHostility.shouldContinue(false, 0));
    }

    @Test
    void settlerTargetsRecentlyHostilePlayerInsideSettlement() {
        assertTrue(SettlerPlayerHostility.shouldContinue(true, 159));
    }

    @Test
    void settlerForgivesPlayerAfterEightSecondsWithoutHostility() {
        assertFalse(SettlerPlayerHostility.shouldContinue(true, 160));
    }
}
