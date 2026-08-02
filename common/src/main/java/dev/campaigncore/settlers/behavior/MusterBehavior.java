package dev.campaigncore.settlers.behavior;

/// ALERT-state behavior for guards/authority: gather at the settlement's muster point. Full
/// alarm/defense choreography (bell ringing, approach positions) lands in M4; the beta stub gives
/// threat states a visible, legible response from day one.
public final class MusterBehavior extends AnchorSeekingBehavior {
    public MusterBehavior() {
        super(dev.campaigncore.settlers.settlement.AnchorType.MUSTER);
    }
}
