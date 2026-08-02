package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionStateTest extends MinecraftTestBase {
    private static final ResourceLocation FOOD = SettlementResources.FOOD;
    private static final ResourceLocation PROCESS = ResourceLocation.fromNamespaceAndPath("settlers", "field_labor");

    @Test
    void storedAccumulates() {
        ProductionState state = new ProductionState();
        state.addStored(FOOD, 1.5);
        state.addStored(FOOD, 2.25);
        assertEquals(3.75, state.stored(FOOD), 1e-9);
    }

    @Test
    void progressAccumulates() {
        ProductionState state = new ProductionState();
        state.addProgress(PROCESS, 0.4);
        state.addProgress(PROCESS, 0.35);
        assertEquals(0.75, state.progress(PROCESS), 1e-9);
    }

    @Test
    void consumeProgressUnitsFloorsAndLeavesRemainder() {
        ProductionState state = new ProductionState();
        state.addProgress(PROCESS, 2.7);
        assertEquals(2.0, state.consumeProgressUnits(PROCESS), 1e-9);
        assertEquals(0.7, state.progress(PROCESS), 1e-9);
        // A second consume with <1 remaining yields nothing and leaves the remainder intact.
        assertEquals(0.0, state.consumeProgressUnits(PROCESS), 1e-9);
        assertEquals(0.7, state.progress(PROCESS), 1e-9);
    }

    @Test
    void rejectsNegativeAndNonFinite() {
        ProductionState state = new ProductionState();
        assertThrows(IllegalArgumentException.class, () -> state.addStored(FOOD, -1.0));
        assertThrows(IllegalArgumentException.class, () -> state.addStored(FOOD, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> state.addStored(FOOD, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> state.addProgress(PROCESS, -0.5));
        assertThrows(IllegalArgumentException.class, () -> state.addProgress(PROCESS, Double.NaN));
    }

    @Test
    void consumeStoredSubtractsUpToAvailable() {
        ProductionState state = new ProductionState();
        state.addStored(FOOD, 10.0);
        assertEquals(4.0, state.consumeStored(FOOD, 4.0), 1e-9);
        assertEquals(6.0, state.stored(FOOD), 1e-9);
        // Consuming more than available returns only what was there and floors at zero.
        assertEquals(6.0, state.consumeStored(FOOD, 100.0), 1e-9);
        assertEquals(0.0, state.stored(FOOD), 1e-9);
    }

    @Test
    void consumeStoredRejectsNegativeAndNonFinite() {
        ProductionState state = new ProductionState();
        state.addStored(FOOD, 5.0);
        assertThrows(IllegalArgumentException.class, () -> state.consumeStored(FOOD, -1.0));
        assertThrows(IllegalArgumentException.class, () -> state.consumeStored(FOOD, Double.NaN));
    }

    @Test
    void copyConstructorIsDeep() {
        ProductionState original = new ProductionState();
        original.addStored(FOOD, 5.0);
        original.setLastProcessedGameTime(100L);
        ProductionState copy = new ProductionState(original);
        copy.addStored(FOOD, 3.0);
        assertEquals(5.0, original.stored(FOOD), 1e-9);
        assertEquals(8.0, copy.stored(FOOD), 1e-9);
        assertEquals(100L, copy.lastProcessedGameTime());
    }
}
