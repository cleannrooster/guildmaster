package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.settlement.AnchorType;

/// ALERT/UNDER_ATTACK behavior for civilians and workers: head for the nearest emergency shelter
/// anchor and stay there. Matches the design's "civilians seek shelter" requirement for the beta's
/// Alert and Under Attack states.
public final class FleeToShelterBehavior extends AnchorSeekingBehavior {
    public FleeToShelterBehavior() {
        super(AnchorType.EMERGENCY_SHELTER);
    }

    @Override
    protected double speed() {
        return 1.4;
    }
}
