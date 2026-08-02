package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.economy.SettlementEconomyTicker;
import dev.campaigncore.settlers.production.SettlementProductionTicker;
import dev.architectury.event.events.common.TickEvent;
import net.minecraft.server.level.ServerLevel;

/// Drives per-settlement runtime logic (threat state, and re-running population so it self-heals) on
/// a staggered cadence, so a level with many settlements never re-scans all of them the same tick.
/// Settlement ticking is the only place the mod does AABB entity queries — anchors and structure
/// roles remain persisted, while block-derived anchors are rebuilt through scheduled audits.
public final class SettlementTicker {
    private static final int TICK_INTERVAL = 20;

    private SettlementTicker() {
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(SettlementTicker::tick);
    }

    private static void tick(ServerLevel level) {
        var settlements = SettlementManager.get(level).all();
        if (settlements.isEmpty()) {
            return;
        }
        long time = level.getGameTime();
        SettlementAnchorUpdater.tick(level, settlements);
        for (int i = 0; i < settlements.size(); i++) {
            Settlement settlement = settlements.get(i);
            // Production runs on its own UUID-staggered 200-tick cadence (self-gated inside), independent
            // of the 20-tick population/threat stagger below — cheap when it's not this settlement's turn.
            SettlementProductionTicker.tick(level, settlement);
            // Food consumption/debt runs right after production on the same self-gated cadence.
            SettlementEconomyTicker.tick(level, settlement);
            // Stagger: each settlement is only evaluated on its own tick-offset within the interval.
            if ((time + i) % TICK_INTERVAL != 0) {
                continue;
            }
            // Re-entrant and cheap (roster-sized entity lookups, no block scans): retrofits anchor
            // assignments onto residents populated by an older build, or re-links residents whose
            // chunk just loaded. Gated on its own config flag inside SettlerPopulator.
            SettlerPopulator.populate(level, settlement);
            SettlementRecruitmentService.tickEmployment(level, settlement);
            if (SettlersConfig.get().population.enableThreatResponses) {
                ThreatStateMachine.tick(level, settlement);
            }
        }
    }
}
