package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// Personal daytime break spent wandering among points inside the settlement.
public final class BreakWanderBehavior extends AnchorSeekingBehavior {
    public BreakWanderBehavior() {
        // Nominal type only; pickTarget draws from the complete settlement anchor table.
        super(AnchorType.SOCIAL);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        List<BlockPos> destinations = new ArrayList<>();
        settlement.anchors().view().values().forEach(destinations::addAll);
        if (destinations.isEmpty()) {
            return Optional.of(settlement.center());
        }
        return Optional.of(destinations.get(settler.getRandom().nextInt(destinations.size())));
    }

    @Override
    protected void onArrive(SettlerEntity settler, Settlement settlement, ServerLevel level) {
        retarget(settler, settlement);
    }

    @Override
    protected double speed() {
        return 0.7;
    }
}
