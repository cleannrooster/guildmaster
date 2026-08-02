package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.economy.FoodCondition;
import dev.campaigncore.settlers.economy.SettlementEconomyModel;
import dev.campaigncore.settlers.production.SettlementProductionTicker;
import net.minecraft.server.level.ServerLevel;

/// Scores conditions the residents can plausibly experience: food, danger, beds, useful work, and
/// recent communal events. The broad three-state result drives visible routines, not numerical buffs.
public final class SettlementMoraleModel {
    private static final long RECENT_EVENT_TICKS = SettlementProductionTicker.DAY_TICKS * 3L;
    private static final long CACHE_TICKS = 200L;
    private static final java.util.Map<java.util.UUID, CachedMorale> CACHE = new java.util.HashMap<>();

    private SettlementMoraleModel() {
    }

    public static MoraleState evaluate(ServerLevel level, Settlement settlement) {
        long now = level.getGameTime();
        CachedMorale cached = CACHE.get(settlement.id());
        if (cached != null && now >= cached.gameTime && now - cached.gameTime < CACHE_TICKS) {
            return cached.state;
        }
        MoraleState state = calculate(level, settlement);
        CACHE.put(settlement.id(), new CachedMorale(now, state));
        return state;
    }

    private static MoraleState calculate(ServerLevel level, Settlement settlement) {
        double production = SettlementProductionTicker.projectedFoodPerDay(level, settlement);
        double consumption = SettlementEconomyModel.dailyConsumption(settlement);
        FoodCondition food = FoodCondition.evaluate(settlement, production, consumption);

        int score = switch (food) {
            case SURPLUS -> 2;
            case STABLE -> 1;
            case STRAINED -> 0;
            case SHORTAGE -> -2;
            case CRITICAL -> -3;
        };
        score += switch (settlement.threatState()) {
            case NORMAL -> 1;
            case ALERT -> 0;
            case RECOVERY -> -1;
            case UNDER_ATTACK, PANIC -> -3;
            case ABANDONED -> -2;
        };

        int population = settlement.roster().size();
        score += settlement.anchors().count(AnchorType.SLEEP) >= population ? 1 : -2;
        long unemployed = settlement.roster().stream()
                .filter(entry -> entry.profile().equals(SettlementRecruitmentService.CIVILIAN))
                .filter(entry -> entry.work().isEmpty() && entry.jobOffer().isEmpty())
                .count();
        if (unemployed == 0) {
            score++;
        } else if (population > 0 && unemployed * 4L > population) {
            score--;
        }

        long cutoff = level.getGameTime() - RECENT_EVENT_TICKS;
        int recentHardship = 0;
        int recentHope = 0;
        for (ChronicleEntry entry : settlement.chronicle()) {
            if (entry.gameTime() < cutoff) {
                continue;
            }
            switch (entry.event()) {
                case "resident_died", "attack_began", "panic" -> recentHardship++;
                case "resident_joined", "traveler_stayed", "expanded", "peace_restored" -> recentHope++;
                default -> {
                }
            }
        }
        score -= Math.min(2, recentHardship);
        score += Math.min(2, recentHope);
        return classify(score);
    }

    static MoraleState classify(int score) {
        if (score >= 4) {
            return MoraleState.THRIVING;
        }
        if (score <= -2) {
            return MoraleState.DISTRESSED;
        }
        return MoraleState.STEADY;
    }

    private record CachedMorale(long gameTime, MoraleState state) {
    }
}
