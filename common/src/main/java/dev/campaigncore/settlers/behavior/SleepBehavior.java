package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.Optional;

/// Night behavior: walk to the settler's own assigned bed (round-robined across the settlement's
/// beds at population time — see SettlerPopulator — so residents actually spread across the
/// available beds instead of each independently drawing a random one and colliding), falling back to
/// any settlement SLEEP anchor and then the settler's home anchor (just outside its front door — see
/// AnchorGenerator), then stand still. Beta shortcut: this is theatrical positioning, not vanilla
/// bed-sleeping (no sleeping-eyes animation state) — documented as acceptable per the design's
/// tolerance for theatrical-over-simulated beta behaviors.
public final class SleepBehavior extends AnchorSeekingBehavior {
    public SleepBehavior() {
        super(AnchorType.SLEEP);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        return settler.sleepAnchor()
                .or(() -> super.pickTarget(settler, settlement))
                .or(settler::homeAnchor);
    }

    @Override
    protected double speed() {
        return 0.9;
    }
}
