package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/// Personal daytime break spent at the settler's assigned home.
public final class BreakAtHomeBehavior extends AnchorSeekingBehavior {
    public BreakAtHomeBehavior() {
        super(AnchorType.HOME);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        return settler.homeAnchor().or(() -> super.pickTarget(settler, settlement));
    }

    @Override
    protected double speed() {
        return 0.8;
    }
}
