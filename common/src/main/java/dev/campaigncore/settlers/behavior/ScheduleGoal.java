package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;
import java.util.Optional;

/// The single top-level Goal that drives every Settler bound to a settlement. Re-evaluates which
/// {@link SettlerBehavior} should be active on a fixed cadence (role + time-of-day + threat state,
/// via {@link ScheduleSelector}) and delegates every tick to whichever one is current.
///
/// Only active for settlers bound to a settlement (unbound dev-spawned settlers keep the vanilla
/// wander/look goals below this one in priority, so they're never idle-frozen).
public final class ScheduleGoal extends Goal {
    private static final int EVALUATE_INTERVAL_TICKS = 40;
    // Once a behavior starts, ordinary schedule re-evaluation can't swap it out again for this long —
    // damps flicker right at a selection boundary (e.g. the day/night edge, or rapid /time changes
    // while testing) so a settler doesn't visibly bounce between two behaviors every few seconds.
    // Threat-driven behaviors (muster/flee) always bypass this and preempt immediately regardless.
    private static final int MIN_COMMITMENT_TICKS = 100;

    private final SettlerEntity settler;
    private SettlerBehavior current;
    private int ticksUntilEvaluate;
    private int ticksSinceStart;

    public ScheduleGoal(SettlerEntity settler) {
        this.settler = settler;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return this.settler.settlementId().isPresent();
    }

    @Override
    public boolean canContinueToUse() {
        return this.canUse();
    }

    @Override
    public void stop() {
        if (this.current != null) {
            this.current.stop(this.settler);
            this.current = null;
        }
    }

    @Override
    public void tick() {
        if (!(this.settler.level() instanceof ServerLevel level)) {
            return;
        }
        Optional<Settlement> settlementOpt = this.settler.settlementId()
                .flatMap(id -> SettlementManager.get(level).byId(id));
        if (settlementOpt.isEmpty()) {
            return;
        }
        Settlement settlement = settlementOpt.get();

        if (--this.ticksUntilEvaluate <= 0) {
            this.ticksUntilEvaluate = EVALUATE_INTERVAL_TICKS;
            SettlerBehavior desired = ScheduleSelector.select(this.settler, settlement, level);
            boolean changed = this.current == null || this.current.getClass() != desired.getClass();
            boolean urgent = desired instanceof MusterBehavior
                    || desired instanceof FleeToShelterBehavior
                    || desired instanceof PanicBehavior;
            boolean committed = this.current != null && this.ticksSinceStart < MIN_COMMITMENT_TICKS;
            if (changed && (urgent || !committed)) {
                if (this.current != null) {
                    this.current.stop(this.settler);
                }
                this.current = desired;
                this.current.start(this.settler, settlement);
                this.ticksSinceStart = 0;
            }
        }
        this.ticksSinceStart++;

        if (this.current != null && !this.current.tick(this.settler, settlement, level)) {
            // Behavior gave up (unreachable/missing anchor) — drop it and force re-evaluation next tick
            // rather than repeating the failure every tick until the normal cadence comes around.
            this.current.stop(this.settler);
            this.current = null;
            this.ticksUntilEvaluate = 0;
        }
    }
}
