package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.detection.FarmSizeClassifier;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementLaborModel;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborEntry;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborReport;
import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.campaigncore.settlers.settlement.ThreatState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The settlement-level economic simulation. Invoked from {@link dev.campaigncore.settlers.settlement.SettlementTicker}
/// for every settlement each server tick; it self-gates to one evaluation per {@link #CADENCE_TICKS},
/// staggered deterministically by settlement UUID so they never all process on the same tick.
///
/// Production is elapsed-time accounted (not driven by animation intervals or {@code WorkBehavior}):
/// each pass credits the ticks that actually elapsed since the last one, so unloaded chunks or long
/// pauses neither lose nor over-produce (elapsed is clamped to one day). The economic core,
/// {@link #process}, is a pure function over {@link ProductionState} + precomputed inputs, so it is
/// unit-testable without a world.
public final class SettlementProductionTicker {
    /// Evaluate each settlement once per this many ticks (10 seconds).
    private static final long CADENCE_TICKS = 200L;
    /// One Minecraft day. Caps elapsed processing and is the denominator for per-day rates.
    public static final long DAY_TICKS = 24000L;

    /// The three food-producing professions, whose combined staffing sets the operational food output.
    private static final List<String> AGRICULTURAL_PROFESSIONS = List.of("farmer", "herder", "fisher");

    private SettlementProductionTicker() {
    }

    /// Per-settlement entry point called from the settlement ticker. Self-gates on the UUID-staggered
    /// cadence, gathers the level-dependent inputs, runs the pure core, and persists only on change.
    public static void tick(ServerLevel level, Settlement settlement) {
        long gameTime = level.getGameTime();
        long phase = Math.floorMod(settlement.id().getLeastSignificantBits(), CADENCE_TICKS);
        if (Math.floorMod(gameTime - phase, CADENCE_TICKS) != 0) {
            return;
        }
        LaborReport report = SettlementLaborModel.report(level, settlement);
        double foodCapacity = FarmSizeClassifier.operationalFoodCapacity(
                level, settlement.structures(), settlement.anchors());
        double agStaffingEff = agriculturalStaffingEfficiency(report);
        boolean changed = process(settlement.production(), gameTime, settlement.threatState(),
                report, foodCapacity, agStaffingEff);
        long harvestPhase = Math.floorMod(settlement.id().getLeastSignificantBits(), 1_200L);
        if (settlement.threatState() == ThreatState.NORMAL
                && report.entry("farmer").active() > 0
                && Math.floorMod(gameTime - harvestPhase, 1_200L) == 0) {
            changed |= FarmSizeClassifier.harvestOneMatureCrop(level, settlement.structures());
        }
        if (changed) {
            SettlementManager.get(level).markDirty();
        }
    }

    /// The pure economic core. Advances {@code state} from its last processed game time to
    /// {@code gameTime} and returns whether the state changed (and therefore must be persisted).
    ///
    /// Production runs only at peace; the first pass and same-tick repeats produce nothing. Outputs
    /// accrue fractionally so totals are independent of evaluation cadence, and food output across all
    /// processes is capped by the settlement's infrastructure food ceiling for the interval.
    public static boolean process(ProductionState state, long gameTime, ThreatState threat,
                                  LaborReport report, double foodCapacity, double agStaffingEff) {
        if (threat != ThreatState.NORMAL) {
            // Production halts entirely; keep the clock current so no backlog accrues to dump on return.
            if (state.lastProcessedGameTime() != gameTime) {
                state.setLastProcessedGameTime(gameTime);
                return true;
            }
            return false;
        }
        long last = state.lastProcessedGameTime();
        if (last == 0L) {
            // First pass ever for this settlement: initialize the clock, produce nothing.
            state.setLastProcessedGameTime(gameTime);
            return true;
        }
        long elapsed = gameTime - last;
        if (elapsed <= 0L) {
            // Same tick (or clock ran backwards): nothing to do — guards against runaway double-counting.
            return false;
        }
        elapsed = Math.min(elapsed, DAY_TICKS);

        Map<ResourceLocation, Double> pending = new HashMap<>();
        for (ProductionProcess proc : ProductionProcesses.all()) {
            LaborEntry entry = report.entry(proc.profession());
            if (entry.required() <= 0 || entry.assigned() <= 0) {
                // No infrastructure demand or nobody assigned — and since profession→workstation is 1:1
                // here, an assigned worker is by construction on the matching anchor type (the generic
                // WORK fallback maps to no food process, so it can never drive this).
                continue;
            }
            double multiplier = entry.effectiveWorkerCount();
            if (multiplier <= 0.0) {
                continue;
            }
            double fractionalCycles = (elapsed / (double) proc.cycleTicks()) * multiplier;
            if (fractionalCycles <= 0.0) {
                continue;
            }
            for (Map.Entry<ResourceLocation, Double> output : proc.outputs().entrySet()) {
                pending.merge(output.getKey(), fractionalCycles * output.getValue(), Double::sum);
            }
        }

        applyFoodCeiling(pending, foodCapacity, agStaffingEff, elapsed);

        for (Map.Entry<ResourceLocation, Double> resource : pending.entrySet()) {
            if (resource.getValue() > 0.0) {
                state.addStored(resource.getKey(), resource.getValue());
            }
        }
        state.setLastProcessedGameTime(gameTime);
        // Always a change: we advanced the persisted clock, which must survive reloads for correct
        // elapsed accounting, even on a pass that produced nothing.
        return true;
    }
    /// Estimated food actually produced per Minecraft day under the settlement's
    /// current staffing and attendance conditions.
    ///
    /// Unlike operationalFoodPerDay, this applies each production process's cycle
    /// duration, food yield, profession-specific staffing, and attendance. The
    /// result is then capped by the settlement's infrastructure food ceiling.
    public static double projectedFoodPerDay(ServerLevel level, Settlement settlement) {
        LaborReport report = SettlementLaborModel.report(level, settlement);

        double foodCapacity = FarmSizeClassifier.operationalFoodCapacity(
                level, settlement.structures(), settlement.anchors());

        double rawFoodPerDay = 0.0;

        for (ProductionProcess process : ProductionProcesses.all()) {
            double foodPerCycle = process.outputs()
                    .getOrDefault(SettlementResources.FOOD, 0.0);

            if (foodPerCycle <= 0.0) {
                continue;
            }

            LaborEntry entry = report.entry(process.profession());

            if (entry.required() <= 0 || entry.assigned() <= 0) {
                continue;
            }

            double multiplier = entry.effectiveWorkerCount();

            if (multiplier <= 0.0) {
                continue;
            }

            double cyclesPerDay =
                    DAY_TICKS / (double) process.cycleTicks();

            rawFoodPerDay += cyclesPerDay * foodPerCycle * multiplier;
        }

        double operationalCeiling =
                foodCapacity * agriculturalStaffingEfficiency(report);

        return Math.min(rawFoodPerDay, operationalCeiling);
    }
    /// Scales the pending food output down (never up) so the total across the three food processes
    /// cannot exceed the settlement's operational food ceiling for the elapsed interval. Non-food
    /// outputs (animal goods, fish) are untouched.
    private static void applyFoodCeiling(Map<ResourceLocation, Double> pending, double foodCapacity,
                                         double agStaffingEff, long elapsed) {
        double rawFood = pending.getOrDefault(SettlementResources.FOOD, 0.0);
        if (rawFood <= 0.0) {
            return;
        }
        double operationalPerDay = foodCapacity * agStaffingEff;
        double ceiling = operationalPerDay * (elapsed / (double) DAY_TICKS);
        if (rawFood > ceiling) {
            pending.put(SettlementResources.FOOD, Math.max(0.0, ceiling));
        }
    }

    /// The settlement's maximum operational food ceiling per Minecraft day,
    /// derived from infrastructure capacity and aggregate agricultural staffing.
    ///
    /// This is not the estimated amount currently produced. It does not account
    /// for individual process yields, cycle durations, or attendance. Use
    /// projectedFoodPerDay for the current production estimate.
    public static double operationalFoodPerDay(
            ServerLevel level,
            Settlement settlement
    ) {
        LaborReport report =
                SettlementLaborModel.report(level, settlement);

        double foodCapacity = FarmSizeClassifier.operationalFoodCapacity(
                level, settlement.structures(), settlement.anchors());

        return foodCapacity
                * agriculturalStaffingEfficiency(report);
    }

    /// Combined effective staffing of the three food professions: effective workers / total required,
    /// clamped to [0,1]. This includes attendance, keeping the infrastructure ceiling proportional to
    /// the same real worker contribution that drives the production processes themselves.
    public static double agriculturalStaffingEfficiency(LaborReport report) {
        int required = 0;
        double effective = 0.0;
        for (String profession : AGRICULTURAL_PROFESSIONS) {
            LaborEntry entry = report.entry(profession);
            required += entry.required();
            effective += entry.effectiveWorkerCount();
        }
        return required <= 0 ? 0.0 : Math.min(1.0, effective / required);
    }
}
