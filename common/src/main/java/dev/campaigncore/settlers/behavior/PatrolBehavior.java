package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;

/// Guard watch behavior: walk the settlement's PATROL anchor loop by day and night (generated from road pieces —
/// see AnchorGenerator) in order, one stop at a time, so the route reads as a perimeter walk rather
/// than random wandering.
public final class PatrolBehavior extends AnchorSeekingBehavior {
    private int index;

    public PatrolBehavior() {
        super(AnchorType.PATROL);
    }

    @Override
    public void start(SettlerEntity settler, Settlement settlement) {
        List<BlockPos> route = settlement.anchors().all(AnchorType.PATROL);
        if (!route.isEmpty()) {
            // Resume near the closest stop rather than always restarting at index 0.
            BlockPos current = settler.blockPosition();
            this.index = 0;
            double best = Double.MAX_VALUE;
            for (int i = 0; i < route.size(); i++) {
                double distSq = route.get(i).distSqr(current);
                if (distSq < best) {
                    best = distSq;
                    this.index = i;
                }
            }
        }
        retarget(settler, settlement);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        List<BlockPos> route = settlement.anchors().all(AnchorType.PATROL);
        return route.isEmpty() ? Optional.empty() : Optional.of(route.get(this.index % route.size()));
    }

    @Override
    protected void onArrive(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        List<BlockPos> route = settlement.anchors().all(AnchorType.PATROL);
        if (route.isEmpty()) {
            return;
        }
        this.index = (this.index + 1) % route.size();
        retarget(settler, settlement);
    }

    @Override
    protected double speed() {
        return 0.9;
    }
}
