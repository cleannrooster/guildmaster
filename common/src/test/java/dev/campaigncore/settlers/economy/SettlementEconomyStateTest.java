package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementEconomyStateTest extends MinecraftTestBase {
    @Test
    void codecRoundTrips() {
        SettlementEconomyState state = new SettlementEconomyState();
        state.addDebt(2.5);
        state.addBundles(3);
        state.setLastConsumptionGameTime(1234L);
        state.setLastConversionDay(56L);

        Tag encoded = SettlementEconomyState.CODEC.encodeStart(NbtOps.INSTANCE, state).result().orElseThrow();
        SettlementEconomyState loaded = SettlementEconomyState.CODEC.parse(NbtOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(2.5, loaded.foodDebt(), 1e-9);
        assertEquals(3, loaded.claimableProvisionBundles());
        assertEquals(1234L, loaded.lastConsumptionGameTime());
        assertEquals(56L, loaded.lastConversionDay());
    }

    @Test
    void rejectsNegativeAndNonFinite() {
        SettlementEconomyState state = new SettlementEconomyState();
        assertThrows(IllegalArgumentException.class, () -> state.addDebt(-1.0));
        assertThrows(IllegalArgumentException.class, () -> state.addDebt(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.reduceDebt(-1.0));
        assertThrows(IllegalArgumentException.class, () -> state.addBundles(-1));
        assertThrows(IllegalArgumentException.class, () -> state.claimBundles(-1));
    }

    @Test
    void bundlesCapAndClaim() {
        SettlementEconomyState state = new SettlementEconomyState();
        assertEquals(5, state.addBundles(5));
        // Saturates at the storage cap of 8: only 3 more can be added.
        assertEquals(3, state.addBundles(10));
        assertEquals(8, state.claimableProvisionBundles());
        assertEquals(3, state.claimBundles(3));
        assertEquals(5, state.claimableProvisionBundles());
        // Claiming more than available returns only what remains.
        assertEquals(5, state.claimBundles(100));
        assertEquals(0, state.claimableProvisionBundles());
    }

    @Test
    void reduceDebtClampsAtZero() {
        SettlementEconomyState state = new SettlementEconomyState();
        state.addDebt(3.0);
        assertEquals(3.0, state.reduceDebt(10.0), 1e-9);
        assertEquals(0.0, state.foodDebt(), 1e-9);
    }

    @Test
    void copyConstructorIsDeep() {
        SettlementEconomyState original = new SettlementEconomyState();
        original.addDebt(4.0);
        original.addBundles(2);
        SettlementEconomyState copy = new SettlementEconomyState(original);
        copy.addDebt(1.0);
        copy.addBundles(1);
        assertEquals(4.0, original.foodDebt(), 1e-9);
        assertEquals(2, original.claimableProvisionBundles());
        assertEquals(5.0, copy.foodDebt(), 1e-9);
        assertEquals(3, copy.claimableProvisionBundles());
    }
}
