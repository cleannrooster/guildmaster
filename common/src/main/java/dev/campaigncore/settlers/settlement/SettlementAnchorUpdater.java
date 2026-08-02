package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.detection.AnchorGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/// Debounced structural anchor rebuilds plus a slow fallback audit, limited to one settlement per tick.
public final class SettlementAnchorUpdater {
    public static final long AUDIT_INTERVAL_TICKS = 72_000L;
    public static final long DIRTY_DEBOUNCE_TICKS = 100L;

    private SettlementAnchorUpdater() {
    }

    public static void tick(ServerLevel level, List<Settlement> settlements) {
        long now = level.getGameTime();
        for (Settlement settlement : settlements) {
            if (settlement.nextAnchorAuditGameTime() == 0L) {
                long phase = Math.floorMod(settlement.id().getLeastSignificantBits(), AUDIT_INTERVAL_TICKS);
                settlement.scheduleAnchorAudit(now + phase);
                SettlementManager.get(level).markDirty();
            }
            boolean due = settlement.anchorsDirty()
                    ? now >= settlement.anchorsRebuildAfterGameTime()
                    : now >= settlement.nextAnchorAuditGameTime();
            if (due) {
                if (rebuild(level, settlement)) {
                    settlement.completeAnchorRebuild(now + AUDIT_INTERVAL_TICKS);
                    SettlementManager.get(level).markDirty();
                }
                return;
            }
        }
    }

    public static boolean rebuild(ServerLevel level, Settlement settlement) {
        if (!AnchorGenerator.canScanAll(level, settlement.structures())) {
            settlement.markAnchorsDirty(level.getGameTime() + DIRTY_DEBOUNCE_TICKS);
            return false;
        }
        AnchorTable fresh = AnchorGenerator.generate(level, settlement.structures(), settlement.center());
        settlement.anchors().replaceWith(fresh);
        reconcile(settlement);
        SettlerPopulator.populate(level, settlement);
        return true;
    }

    private static void reconcile(Settlement settlement) {
        Set<BlockPos> beds = new HashSet<>();
        Set<BlockPos> work = new HashSet<>();
        for (ResidentEntry entry : settlement.roster()) {
            if (entry.home().filter(pos -> settlement.anchors().contains(AnchorType.HOME, pos)).isEmpty()) {
                entry.clearHome();
            }
            if (entry.sleep().filter(pos -> settlement.anchors().contains(AnchorType.SLEEP, pos)
                    && beds.add(pos)).isEmpty()) {
                entry.clearSleep();
            }
            if (entry.work().isPresent()) {
                AnchorType type = entry.workType().orElse(AnchorType.WORK);
                BlockPos pos = entry.work().orElseThrow();
                if (!settlement.anchors().contains(type, pos) || !work.add(pos)) {
                    entry.clearWork();
                    entry.clearJobOffer();
                }
            }
        }
    }
}
