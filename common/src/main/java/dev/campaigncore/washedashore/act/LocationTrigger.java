package dev.campaigncore.washedashore.act;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Optional;

/**
 * Data-defined "walk near a place, something happens" rule. Fires for a player standing
 * within {@code radius} of a layout {@link WashedAshoreInstance#SLOTS slot}, gated by an
 * optional stage window / named condition / one-time {@code discover} marker, then applies
 * generic effects (discover, advance stages, message) and an optional named Java handler.
 */
public record LocationTrigger(
        String location,
        int radius,
        Optional<WashedAshoreStage> belowStage,
        Optional<WashedAshoreStage> minStage,
        Optional<String> condition,
        Optional<ResourceLocation> discover,
        List<WashedAshoreStage> advanceTo,
        Optional<String> message,
        Optional<String> handler
) {
    public LocationTrigger {
        if(location==null||location.isBlank())throw new IllegalArgumentException("location is required");
        if(radius<=0)throw new IllegalArgumentException("radius must be positive");
        advanceTo = advanceTo==null ? List.of() : List.copyOf(advanceTo);
    }
}
