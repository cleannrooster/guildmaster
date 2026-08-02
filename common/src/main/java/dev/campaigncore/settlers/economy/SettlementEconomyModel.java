package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.production.SettlementProductionTicker;
import dev.campaigncore.settlers.production.SettlementResources;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.server.level.ServerLevel;

/// Derives economy reports and the food condition from authoritative state.
///
/// Projected production uses the same production-process yields, cycle times,
/// staffing, attendance, and infrastructure ceiling as the simulation.
public final class SettlementEconomyModel {
    /// One abstract food unit consumed per permanent resident per Minecraft day.
    public static final double FOOD_PER_RESIDENT_PER_DAY = 1.0;

    private SettlementEconomyModel() {
    }

    /// Consumption demand for a settlement: its persistent roster times the
    /// per-resident daily rate.
    public static double dailyConsumption(Settlement settlement) {
        return settlement.roster().size()
                * FOOD_PER_RESIDENT_PER_DAY;
    }

    public static SettlementEconomyReport report(
            ServerLevel level,
            Settlement settlement
    ) {
        int population = settlement.roster().size();

        double production =
                SettlementProductionTicker.projectedFoodPerDay(
                        level,
                        settlement
                );

        double consumption =
                dailyConsumption(settlement);

        double storedFood =
                settlement.production()
                        .stored(SettlementResources.FOOD);

        double foodDebt =
                settlement.economy().foodDebt();

        double foodStorageReserve = SettlementFoodStorageReserve.count(level, settlement)
                * SettlementFoodStorageReserve.FOOD_PER_STORAGE_BLOCK;

        FoodCondition condition =
                FoodCondition.evaluate(
                        settlement,
                        production,
                        consumption
                );

        return new SettlementEconomyReport(
                population,
                production,
                consumption,
                production + foodStorageReserve - consumption,
                foodStorageReserve,
                storedFood,
                foodDebt,
                condition
        );
    }
}
