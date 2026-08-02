package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.detection.WorkstationCounter;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.entity.data.SettlerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The single source of truth for how much labor a settlement's infrastructure demands, and how well
/// that demand is currently staffed and attended. Both roster generation (at conversion, via
/// {@link dev.campaigncore.settlers.detection.SettlementConverter}) and the economic simulation (per tick, via
/// {@link dev.campaigncore.settlers.production.SettlementProductionTicker}) consult it, so the anchors-per-worker
/// ratios and profession caps live here once and only once.
///
/// Demand is derived purely from the {@link AnchorTable} (per-chunk-capped by
/// {@link WorkstationCounter}); staffing/attendance additionally resolve each roster entry's live
/// entity by id — never by an AABB scan.
public final class SettlementLaborModel {
    /// The professions this model tracks demand for. Order is display order for {@code /settlers info}.
    public static final List<String> TRACKED_PROFESSIONS =
            List.of("farmer", "herder", "fisher", "shrine_keeper", "smith");

    /// A resident counts as "attending" when its live entity sits within this many blocks of its work
    /// anchor. Squared for a cheap distance check.
    /// Matches AnchorSeekingBehavior's local activity radius: workers circulating around their site
    /// still count as attending rather than losing production unless they leave the work area.
    private static final double WORK_PROXIMITY = 8.0;
    private static final double WORK_PROXIMITY_SQR = WORK_PROXIMITY * WORK_PROXIMITY;

    private SettlementLaborModel() {
    }

    /// Infrastructure-derived job demand for a profession, from the settlement's anchor table.
    public static int requiredWorkers(Settlement settlement, String profession) {
        return requiredWorkers(settlement.anchors(), profession);
    }

    /// Infrastructure-derived job demand for a profession. These are the exact anchors-per-worker
    /// ratios and caps the roster generator has always used — the smith's workshop-presence floor is a
    /// structure-derived concern that stays in the converter (a settlement with a workshop but no WORK
    /// anchor still wants one smith), so it is not represented here.
    public static int requiredWorkers(AnchorTable anchors, String profession) {
        return switch (profession) {
            case "farmer" -> workerCount(anchors, AnchorType.CROP_TENDING, 2, 6);
            case "herder" -> workerCount(anchors, AnchorType.ANIMAL_TENDING, 1, 8);
            case "fisher" -> workerCount(anchors, AnchorType.FISHING, 1, 8);
            case "shrine_keeper" -> workerCount(anchors, AnchorType.SHRINE, 4, 2);
            case "smith" -> workerCount(anchors, AnchorType.WORK, 3, 4);
            default -> 0;
        };
    }

    /// 1 worker per {@code anchorsPerWorker} effective (per-chunk-capped) anchors of the given type,
    /// clamped to {@code [1, max]} — or 0 when the settlement has none of that anchor type at all.
    public static int workerCount(AnchorTable anchors, AnchorType type, int anchorsPerWorker, int max) {
        int effective = WorkstationCounter.effectiveCount(anchors, type);
        if (effective == 0) {
            return 0;
        }
        return Mth.clamp((effective + anchorsPerWorker - 1) / anchorsPerWorker, 1, max);
    }

    /// Snapshots required/assigned/active for every tracked profession. {@code assigned} counts roster
    /// entries whose resolved profession matches; {@code active} narrows that to residents whose live
    /// entity is present, alive, at its work anchor, in a settlement at peace.
    public static LaborReport report(ServerLevel level, Settlement settlement) {
        boolean atPeace = settlement.threatState() == ThreatState.NORMAL;
        Map<String, LaborEntry> professions = new LinkedHashMap<>();
        for (String profession : TRACKED_PROFESSIONS) {
            int required = requiredWorkers(settlement, profession);
            int assigned = 0;
            int active = 0;
            for (ResidentEntry entry : settlement.roster()) {
                if (!professionOf(entry).equals(profession)) {
                    continue;
                }
                assigned++;
                if (atPeace && isActive(level, entry)) {
                    active++;
                }
            }
            professions.put(profession, new LaborEntry(required, assigned, active));
        }
        return new LaborReport(professions);
    }

    /// The resolved profession string for a roster entry: the datapack profile's profession, falling
    /// back to the profile id's path (which the converter authors to equal the profession name).
    public static String professionOf(ResidentEntry entry) {
        return SettlerDataManager.profile(entry.profile())
                .map(SettlerProfile::profession)
                .orElse(entry.profile().getPath());
    }

    /// A resident is "active" when its live entity exists and is alive, it has a bound work anchor, and
    /// the entity is within {@link #WORK_PROXIMITY} blocks of that anchor. Callers gate on the
    /// settlement being at peace separately (see {@link #report}); this method does not re-check it.
    private static boolean isActive(ServerLevel level, ResidentEntry entry) {
        if (entry.workType().isEmpty() || entry.work().isEmpty()) {
            return false;
        }
        Entity entity = entry.entityId().map(level::getEntity).orElse(null);
        if (!(entity instanceof SettlerEntity settler) || !settler.isAlive()) {
            return false;
        }
        BlockPos work = entry.work().get();
        return settler.distanceToSqr(work.getX() + 0.5, work.getY() + 0.5, work.getZ() + 0.5) <= WORK_PROXIMITY_SQR;
    }

    /// Required workers assigned to attend a profession, i.e. an intent-level staffing efficiency in
    /// [0,1]; and the fraction of assigned residents currently attending their workstation.
    public record LaborEntry(int required, int assigned, int active) {
        public double staffingEfficiency() {
            return required <= 0 ? 0.0 : Math.min(1.0, assigned / (double) required);
        }

        public double attendanceEfficiency() {
            return assigned <= 0 ? 0.0 : active / (double) assigned;
        }

        /// Number of workers actually contributing to production. Assignment alone provides one-third
        /// output; attending the workplace supplies the remaining two-thirds, so a working settler
        /// yields exactly three times an absent one. Surplus workers are capped at infrastructure demand.
        public double effectiveWorkerCount() {
            int demand = Math.max(0, required);
            int staffedWorkers = Math.min(Math.max(0, assigned), demand);
            int attendingWorkers = Math.min(Math.max(0, active), demand);
            return staffedWorkers / 3.0 + attendingWorkers * (2.0 / 3.0);
        }
    }

    public record LaborReport(Map<String, LaborEntry> professions) {
        public LaborEntry entry(String profession) {
            return professions.getOrDefault(profession, new LaborEntry(0, 0, 0));
        }
    }
}
