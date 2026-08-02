package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.production.ProductionState;
import dev.campaigncore.settlers.production.SettlementResources;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the pure {@link SettlementEconomyTicker#consume} core.
class SettlementEconomyConsumptionTest extends MinecraftTestBase {
    private static final long DAY = SettlementEconomyTicker.MINECRAFT_DAY_TICKS;

    private static ProductionState food(double amount) {
        ProductionState prod = new ProductionState();
        if (amount > 0) {
            prod.addStored(SettlementResources.FOOD, amount);
        }
        return prod;
    }

    @Test
    void firstPassInitializesClockAndConsumesNothing() {
        SettlementEconomyState econ = new SettlementEconomyState();
        ProductionState prod = food(10.0);
        boolean changed = SettlementEconomyTicker.consume(econ, prod, 1000L, 5);
        assertTrue(changed);
        assertEquals(1000L, econ.lastConsumptionGameTime());
        assertEquals(10.0, prod.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(0.0, econ.foodDebt(), 1e-9);
    }

    @Test
    void elapsedConsumptionReducesFood() {
        SettlementEconomyState econ = new SettlementEconomyState();
        econ.setLastConsumptionGameTime(1L);
        ProductionState prod = food(10.0);
        SettlementEconomyTicker.consume(econ, prod, 1L + DAY, 5); // 5 residents * 1 day = 5 food
        assertEquals(5.0, prod.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(0.0, econ.foodDebt(), 1e-9);
    }

    @Test
    void partialAvailabilityCreatesDebt() {
        SettlementEconomyState econ = new SettlementEconomyState();
        econ.setLastConsumptionGameTime(1L);
        ProductionState prod = food(2.0);
        SettlementEconomyTicker.consume(econ, prod, 1L + DAY, 5); // needs 5, only 2 on hand
        assertEquals(0.0, prod.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(3.0, econ.foodDebt(), 1e-9);
    }

    @Test
    void leftoverFoodReducesDebtBeforeSurplus() {
        SettlementEconomyState econ = new SettlementEconomyState();
        econ.setLastConsumptionGameTime(1L);
        econ.addDebt(3.0);
        ProductionState prod = food(10.0);
        // Needs 5 for today's 5 residents; 5 remains, and 3 of that pays down the prior debt.
        SettlementEconomyTicker.consume(econ, prod, 1L + DAY, 5);
        assertEquals(2.0, prod.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(0.0, econ.foodDebt(), 1e-9);
    }

    @Test
    void elapsedIsClampedToSevenDays() {
        SettlementEconomyState econ = new SettlementEconomyState();
        econ.setLastConsumptionGameTime(1L);
        ProductionState prod = food(1000.0);
        // 100 days elapsed, 1 resident: clamped to 7 days => only 7 food consumed.
        SettlementEconomyTicker.consume(econ, prod, 1L + 100L * DAY, 1);
        assertEquals(993.0, prod.stored(SettlementResources.FOOD), 1e-9);
        assertEquals(0.0, econ.foodDebt(), 1e-9);
    }

    @Test
    void sameTickIsNoOp() {
        SettlementEconomyState econ = new SettlementEconomyState();
        econ.setLastConsumptionGameTime(500L);
        ProductionState prod = food(10.0);
        assertFalse(SettlementEconomyTicker.consume(econ, prod, 500L, 5));
        assertEquals(10.0, prod.stored(SettlementResources.FOOD), 1e-9);
    }

    @Test
    void storageIsRequestedOnlyForWholeBlocksNeededByShortfall() {
        assertEquals(0, SettlementEconomyTicker.storageBlocksForShortfall(5.0, 0.0, 5.0));
        assertEquals(1, SettlementEconomyTicker.storageBlocksForShortfall(5.0, 0.0, 4.75));
        assertEquals(3, SettlementEconomyTicker.storageBlocksForShortfall(5.0, 2.0, 4.5));
    }
}
