package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/// Evening/idle behavior: gather at the settlement's social anchor (or plaza as a fallback), giving
/// the settlement a legible communal gathering point per the design's "public gathering" requirement.
public final class SocializeBehavior extends AnchorSeekingBehavior {
    public SocializeBehavior() {
        super(AnchorType.SOCIAL);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        Optional<BlockPos> social = super.pickTarget(settler, settlement);
        return social.isPresent() ? social : settlement.anchors().random(AnchorType.PLAZA, settler.getRandom());
    }
}
