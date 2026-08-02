package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/// PANIC-state behavior for civilians and workers when no living guards remain: scatter, fast, to a
/// random anchor anywhere in the settlement — with nobody to direct an orderly retreat, everyone
/// runs *somewhere* rather than converging on the shelter point the way FleeToShelterBehavior does.
public final class PanicBehavior extends AnchorSeekingBehavior {
    public PanicBehavior() {
        // Nominal type only; pickTarget below draws from every anchor type at once.
        super(AnchorType.EMERGENCY_SHELTER);
    }

    @Override
    protected Optional<BlockPos> pickTarget(SettlerEntity settler, Settlement settlement) {
        List<BlockPos> all = new ArrayList<>();
        settlement.anchors().view().values().forEach(all::addAll);
        if (all.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(all.get(settler.getRandom().nextInt(all.size())));
    }

    @Override
    protected double speed() {
        return 1.5;
    }
}
