package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborEntry;
import dev.campaigncore.settlers.settlement.SettlementLaborModel.LaborReport;
import dev.campaigncore.settlers.settlement.ThreatState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises the pure economic core {@link SettlementProductionTicker#process}.
class SettlementProductionProcessTest extends MinecraftTestBase {
    private static final long DAY = SettlementProductionTicker.DAY_TICKS;
    private static final double UNCAPPED_FOOD = 100_000.0;

    private static LaborReport report(LaborEntry farmer, LaborEntry herder, LaborEntry fisher) {
        Map<String, LaborEntry> map = new LinkedHashMap<>();
        map.put("farmer", farmer);
        map.put("herder", herder);
        map.put("fisher", fisher);
        return new LaborReport(map);
    }

    private static LaborReport farmersOnly(int required, int assigned, int active) {
        LaborEntry none = new LaborEntry(0, 0, 0);
        return report(new LaborEntry(required, assigned, active), none, none);
    }

    @Test
    void firstPassProducesNothingAndInitializesClock() {
        ProductionState state = new ProductionState();
        boolean changed = SettlementProductionTicker.process(
                state, 1000L, ThreatState.NORMAL, farmersOnly(2, 2, 2), UNCAPPED_FOOD, 1.0);
        assertTrue(changed);
        assertEquals(1000L, state.lastProcessedGameTime());
        assertEquals(0.0, state.stored(SettlementResources.FOOD), 1e-9);
    }

    @Test
    void noProductionDuringThreat() {
        ProductionState state = new ProductionState();
        state.setLastProcessedGameTime(1L);
        boolean changed = SettlementProductionTicker.process(
                state, 1L + DAY, ThreatState.UNDER_ATTACK, farmersOnly(2, 2, 2), UNCAPPED_FOOD, 1.0);
        assertTrue(changed); // clock advanced
        assertEquals(1L + DAY, state.lastProcessedGameTime());
        assertEquals(0.0, state.stored(SettlementResources.FOOD), 1e-9);
    }

    @Test
    void repeatedTickAtSameGameTimeProducesNothing() {
        ProductionState state = new ProductionState();
        state.setLastProcessedGameTime(1L);
        SettlementProductionTicker.process(state, 1L + DAY, ThreatState.NORMAL, farmersOnly(2, 2, 2), UNCAPPED_FOOD, 1.0);
        double after = state.stored(SettlementResources.FOOD);
        assertTrue(after > 0.0);

        boolean changed = SettlementProductionTicker.process(
                state, 1L + DAY, ThreatState.NORMAL, farmersOnly(2, 2, 2), UNCAPPED_FOOD, 1.0);
        assertFalse(changed, "same-tick re-run must be a no-op");
        assertEquals(after, state.stored(SettlementResources.FOOD), 1e-9);
    }

    @Test
    void outputScalesUpWithAssignedWorkers() {
        double full = foodAfterDay(farmersOnly(2, 2, 2));
        double half = foodAfterDay(farmersOnly(2, 1, 1));
        assertEquals(2.0, full / half, 1e-6,
                "two effective workers must produce exactly twice as much as one");
    }

    @Test
    void workingYieldsTripleAbsentAssignment() {
        double noAttendance = foodAfterDay(farmersOnly(2, 2, 0));
        double fullAttendance = foodAfterDay(farmersOnly(2, 2, 2));
        assertTrue(fullAttendance > noAttendance);
        assertEquals(3.0, fullAttendance / noAttendance, 1e-6);
    }

    @Test
    void foodOutputCappedByInfrastructureCeiling() {
        // Three fully-staffed food professions would raw-produce ~55 food/day; a tiny farm caps it.
        LaborReport report = report(new LaborEntry(2, 2, 2), new LaborEntry(2, 2, 2), new LaborEntry(2, 2, 2));
        ProductionState state = new ProductionState();
        state.setLastProcessedGameTime(1L);
        double foodCapacity = 5.0;
        SettlementProductionTicker.process(state, 1L + DAY, ThreatState.NORMAL, report, foodCapacity, 1.0);
        // Ceiling = foodCapacity * agStaffingEff(=1) * (elapsed/day = 1) = 5.
        assertEquals(5.0, state.stored(SettlementResources.FOOD), 1e-6);
    }

    @Test
    void nonFoodOutputsIgnoreFoodCeiling() {
        LaborReport report = report(new LaborEntry(2, 2, 2), new LaborEntry(2, 2, 2), new LaborEntry(2, 2, 2));
        ProductionState state = new ProductionState();
        state.setLastProcessedGameTime(1L);
        SettlementProductionTicker.process(state, 1L + DAY, ThreatState.NORMAL, report, 5.0, 1.0);
        // Two workers complete 40 cycles: 10 animal goods and 10 fish, unaffected by the
        // food cap that crushed FOOD down to 5.
        assertEquals(10.0, state.stored(SettlementResources.ANIMAL_GOODS), 1e-6);
        assertEquals(10.0, state.stored(SettlementResources.FISH), 1e-6);
        assertEquals(5.0, state.stored(SettlementResources.FOOD), 1e-6);
    }

    @Test
    void workersBeyondInfrastructureDemandDoNotIncreaseOutput() {
        double exactlyStaffed = foodAfterDay(farmersOnly(2, 2, 2));
        double overstaffed = foodAfterDay(farmersOnly(2, 4, 4));
        assertEquals(exactlyStaffed, overstaffed, 1e-6);
    }

    @Test
    void eightFishersOrHerdersProduceNinetySixFoodPerDay() {
        LaborEntry none = new LaborEntry(0, 0, 0);
        LaborEntry eightWorkers = new LaborEntry(8, 8, 8);

        assertEquals(96.0, foodAfterDay(report(none, none, eightWorkers)), 1e-6);
        assertEquals(96.0, foodAfterDay(report(none, eightWorkers, none)), 1e-6);
    }

    @Test
    void normalCadenceMatchesOneFullDayPass() {
        LaborReport labor = farmersOnly(2, 2, 2);
        double foodCapacity = 8.0;

        ProductionState onePass = new ProductionState();
        onePass.setLastProcessedGameTime(1L);
        SettlementProductionTicker.process(
                onePass, 1L + DAY, ThreatState.NORMAL, labor, foodCapacity, 1.0);

        ProductionState cadence = new ProductionState();
        cadence.setLastProcessedGameTime(1L);
        for (long elapsed = 200L; elapsed <= DAY; elapsed += 200L) {
            SettlementProductionTicker.process(
                    cadence, 1L + elapsed, ThreatState.NORMAL, labor, foodCapacity, 1.0);
        }

        assertEquals(onePass.stored(SettlementResources.FOOD),
                cadence.stored(SettlementResources.FOOD), 1e-6);
    }

    /// Runs one full-day pass from a clean state and returns the food produced (uncapped ceiling).
    private static double foodAfterDay(LaborReport report) {
        ProductionState state = new ProductionState();
        state.setLastProcessedGameTime(1L);
        SettlementProductionTicker.process(state, 1L + DAY, ThreatState.NORMAL, report, UNCAPPED_FOOD, 1.0);
        return state.stored(SettlementResources.FOOD);
    }
}
