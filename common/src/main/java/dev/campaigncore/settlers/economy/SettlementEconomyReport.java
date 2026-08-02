package dev.campaigncore.settlers.economy;

/// An immutable snapshot of a settlement's economy for display and decision-making. Purely derived from
/// authoritative state (production, roster, infrastructure) — building one mutates nothing. Consumed by
/// {@code /settlers info}, the reeve interaction, and any future GUI/work-order code.
public record SettlementEconomyReport(
        int population,
        double dailyFoodProduction,
        double dailyFoodConsumption,
        double projectedDailySurplus,
        double foodStorageReserve,
        double storedFood,
        double foodDebt,
        FoodCondition condition
) {
}
