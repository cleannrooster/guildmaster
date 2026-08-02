package dev.campaigncore.settlers.economy;

import com.mojang.serialization.Codec;
import dev.campaigncore.settlers.production.SettlementResources;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.util.StringRepresentable;

/// A settlement's food posture, derived (never persisted) from its authoritative food reserve, food
/// debt, projected daily production, and daily consumption. Deriving it on demand means it can never go
/// stale relative to the numbers it summarizes.
public enum FoodCondition implements StringRepresentable {
    SURPLUS("surplus"),
    STABLE("stable"),
    STRAINED("strained"),
    SHORTAGE("shortage"),
    CRITICAL("critical");

    public static final Codec<FoodCondition> CODEC = StringRepresentable.fromEnum(FoodCondition::values);

    /// A settlement in SURPLUS must project daily production at least this fraction of consumption above
    /// consumption (i.e. production >= 1.25x consumption).
    private static final double SURPLUS_MARGIN = 0.25;

    private final String name;

    FoodCondition(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }

    /// Classifies the settlement. Debt dominates (any debt is at best a SHORTAGE); otherwise the verdict
    /// comes from production-vs-consumption plus how many days the current reserve covers.
    public static FoodCondition evaluate(Settlement settlement, double projectedDailyProduction,
                                         double dailyConsumption) {
        double storedFood = settlement.production().stored(SettlementResources.FOOD);
        double foodDebt = settlement.economy().foodDebt();

        if (dailyConsumption <= 0.0) {
            // No residents to feed — trivially fine.
            return STABLE;
        }
        if (foodDebt >= dailyConsumption) {
            return CRITICAL;
        }
        if (foodDebt > 0.0) {
            return SHORTAGE;
        }

        double surplus = projectedDailyProduction - dailyConsumption;
        if (surplus >= SURPLUS_MARGIN * dailyConsumption && storedFood - dailyConsumption >= dailyConsumption) {
            // Production comfortably exceeds need and more than a day's reserve remains after today.
            return SURPLUS;
        }
        if (surplus >= 0.0) {
            return STABLE;
        }
        // Underproducing: STRAINED while the reserve still covers a day, SHORTAGE once it can't.
        return storedFood >= dailyConsumption ? STRAINED : SHORTAGE;
    }
}
