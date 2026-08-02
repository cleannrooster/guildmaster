package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.MinecraftTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementMoraleModelTest extends MinecraftTestBase {
    @Test
    void scoreMapsToThreeBroadStates() {
        assertEquals(MoraleState.DISTRESSED, SettlementMoraleModel.classify(-2));
        assertEquals(MoraleState.STEADY, SettlementMoraleModel.classify(-1));
        assertEquals(MoraleState.STEADY, SettlementMoraleModel.classify(3));
        assertEquals(MoraleState.THRIVING, SettlementMoraleModel.classify(4));
    }

    @Test
    void moralePoliciesChangeOptionalRoutineBehavior() {
        assertFalse(MoraleState.DISTRESSED.allowsOptionalConversation());
        assertFalse(MoraleState.DISTRESSED.takesOptionalErrands());
        assertTrue(MoraleState.DISTRESSED.spendsBreakAtHome(false));
        assertTrue(MoraleState.THRIVING.allowsOptionalConversation());
        assertFalse(MoraleState.THRIVING.spendsBreakAtHome(true));
    }
}
