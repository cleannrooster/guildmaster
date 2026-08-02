package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

/// Daytime worker/authority behavior: walk to the settler's bound work anchor (falling back to any
/// WORK anchor), then periodically trigger the work animation. Theatrical per the design — no
/// simulated production, just visible, legible activity at the station.
public final class WorkBehavior extends AnchorSeekingBehavior {
    private static final int ANIM_INTERVAL_TICKS = 60;

    private int animCooldown;

    public WorkBehavior() {
        super(AnchorType.WORK);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        return settler.workAnchor().or(() -> super.pickTarget(settler, settlement));
    }

    @Override
    protected void onArrive(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        if (--this.animCooldown <= 0) {
            this.animCooldown = ANIM_INTERVAL_TICKS;
            settler.triggerWorkAnimation();
            settler.getLookControl().setLookAt(target().map(BlockPos::getCenter).orElse(settler.position()));
        }
    }
}
