package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.production.SettlementResources;
import dev.campaigncore.settlers.settlement.Settlement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodConditionTest extends MinecraftTestBase {
    private static Settlement settlement(double storedFood, double foodDebt) {
        Settlement s = EconomyTestSupport.minimalSettlement();
        if (storedFood > 0) {
            s.production().addStored(SettlementResources.FOOD, storedFood);
        }
        if (foodDebt > 0) {
            s.economy().addDebt(foodDebt);
        }
        return s;
    }

    @Test
    void criticalWhenDebtCoversAtLeastOneDay() {
        assertEquals(FoodCondition.CRITICAL, FoodCondition.evaluate(settlement(0, 5.0), 10.0, 5.0));
    }

    @Test
    void shortageWhenDebtBelowOneDay() {
        assertEquals(FoodCondition.SHORTAGE, FoodCondition.evaluate(settlement(0, 2.0), 10.0, 5.0));
    }

    @Test
    void surplusWhenProductionAmpleAndReserveDeep() {
        // surplus = 10-5 = 5 (>= 1.25); stored 20, after a day's 5 still 15 (>= one day) => SURPLUS.
        assertEquals(FoodCondition.SURPLUS, FoodCondition.evaluate(settlement(20.0, 0), 10.0, 5.0));
    }

    @Test
    void stableWhenProductionMeetsConsumption() {
        assertEquals(FoodCondition.STABLE, FoodCondition.evaluate(settlement(5.0, 0), 5.0, 5.0));
    }

    @Test
    void strainedWhenUnderproducingButReserveCoversDay() {
        assertEquals(FoodCondition.STRAINED, FoodCondition.evaluate(settlement(6.0, 0), 3.0, 5.0));
    }

    @Test
    void shortageWhenUnderproducingAndReserveShort() {
        assertEquals(FoodCondition.SHORTAGE, FoodCondition.evaluate(settlement(2.0, 0), 3.0, 5.0));
    }

    @Test
    void stableWhenNoResidents() {
        assertEquals(FoodCondition.STABLE, FoodCondition.evaluate(settlement(0, 0), 0.0, 0.0));
    }
}
