package dev.campaigncore.settlers.economy;

import dev.campaigncore.settlers.production.ProductionState;
import dev.campaigncore.settlers.production.SettlementProductionTicker;
import dev.campaigncore.settlers.production.SettlementResources;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.server.level.ServerLevel;

/// The consumption/debt/conversion half of the economy, run after production each pass. Like
/// {@link SettlementProductionTicker} it is elapsed-time accounted and self-gates to a UUID-staggered
/// cadence, so it never depends on any particular server tick firing. The two mutating steps —
/// {@link #consume} and {@link #convert} — are pure functions over {@link SettlementEconomyState} +
/// {@link ProductionState} + primitives, so the whole economy is unit-testable without a world.
public final class SettlementEconomyTicker {
    /// Match production's evaluation cadence and per-UUID stagger.
    private static final long CADENCE_TICKS = 200L;
    /// One Minecraft day, shared with production so the two halves agree on "per day".
    public static final long MINECRAFT_DAY_TICKS = SettlementProductionTicker.DAY_TICKS;
    /// Never process more than a week of consumption in one pass — bounds catch-up after a long pause or
    /// a clock jump so a settlement can't be starved retroactively into deep debt.
    public static final long MAX_CONSUMPTION_ELAPSED = 7L * MINECRAFT_DAY_TICKS;

    private SettlementEconomyTicker() {
    }

    /// Per-settlement entry point, called from the settlement ticker after the production tick.
    public static void tick(ServerLevel level, Settlement settlement) {
        long gameTime = level.getGameTime();
        long phase = Math.floorMod(settlement.id().getLeastSignificantBits(), CADENCE_TICKS);
        if (Math.floorMod(gameTime - phase, CADENCE_TICKS) != 0) {
            return;
        }
        SettlementEconomyState economy = settlement.economy();
        ProductionState production = settlement.production();
        int population = settlement.roster().size();
        double dailyConsumption = SettlementEconomyModel.dailyConsumption(settlement);

        boolean changed = drawHayForShortfall(level, settlement, gameTime, population);
        changed |= consume(economy, production, gameTime, population);

        if (changed) {
            SettlementManager.get(level).markDirty();
        }
    }

    /// Converts only enough tagged food-storage blocks to cover food that ordinary stores cannot
    /// supply. Whole blocks become stored food first, so unused fractions remain for the next pass.
    private static boolean drawHayForShortfall(ServerLevel level, Settlement settlement,
                                               long gameTime, int population) {
        SettlementEconomyState economy = settlement.economy();
        long last = economy.lastConsumptionGameTime();
        if (last == 0L || gameTime <= last) {
            return false;
        }
        long elapsed = Math.min(gameTime - last, MAX_CONSUMPTION_ELAPSED);
        double required = population * SettlementEconomyModel.FOOD_PER_RESIDENT_PER_DAY
                * elapsed / (double) MINECRAFT_DAY_TICKS;
        double shortfall = required + economy.foodDebt()
                - settlement.production().stored(SettlementResources.FOOD);
        if (shortfall <= 1.0e-9) {
            return false;
        }
        int requested = storageBlocksForShortfall(required, economy.foodDebt(),
                settlement.production().stored(SettlementResources.FOOD));
        int consumed = SettlementFoodStorageReserve.consume(level, settlement, requested);
        if (consumed <= 0) {
            return false;
        }
        settlement.production().addStored(SettlementResources.FOOD,
                consumed * SettlementFoodStorageReserve.FOOD_PER_STORAGE_BLOCK);
        return true;
    }

    static int storageBlocksForShortfall(double required, double debt, double storedFood) {
        double shortfall = required + debt - storedFood;
        return shortfall <= 1.0e-9 ? 0
                : (int) Math.ceil(shortfall / SettlementFoodStorageReserve.FOOD_PER_STORAGE_BLOCK);
    }

    /// Consumes the food the population needed over the elapsed interval, accruing debt for any
    /// shortfall and paying down existing debt with any leftover food. Returns whether state changed.
    public static boolean consume(SettlementEconomyState economy, ProductionState production,
                                  long gameTime, int population) {
        long last = economy.lastConsumptionGameTime();
        if (last == 0L) {
            // First pass ever: initialize the clock, consume nothing (no retroactive starvation).
            economy.setLastConsumptionGameTime(gameTime);
            return true;
        }
        long elapsed = gameTime - last;
        if (elapsed <= 0L) {
            return false;
        }
        elapsed = Math.min(elapsed, MAX_CONSUMPTION_ELAPSED);

        double required = population * SettlementEconomyModel.FOOD_PER_RESIDENT_PER_DAY
                * elapsed / (double) MINECRAFT_DAY_TICKS;
        if (required > 0.0) {
            double consumed = production.consumeStored(SettlementResources.FOOD, required);
            double unmet = required - consumed;
            if (unmet > 1.0e-9) {
                economy.addDebt(unmet);
            }
        }
        // Any food still on hand pays down prior debt before it can ever become convertible surplus.
        double remaining = production.stored(SettlementResources.FOOD);
        if (economy.foodDebt() > 0.0 && remaining > 0.0) {
            double repaid = economy.reduceDebt(Math.min(economy.foodDebt(), remaining));
            production.consumeStored(SettlementResources.FOOD, repaid);
        }
        economy.setLastConsumptionGameTime(gameTime);
        return true;
    }

    /// Converts genuine surplus (food above a one-day protected reserve) into provision bundles, at most
    /// once per Minecraft day, and never while in debt, in shortage, or under any non-NORMAL threat.
    /// Returns whether state changed.
}
