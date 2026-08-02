package dev.campaigncore.washedashore.encounter;

import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Optional;

/**
 * The data-defined buildup meter that gates a regional encounter: a 0..{@code threshold} value that
 * rises by {@code fillUp} or falls by {@code fillDown} each tick depending on the quest's fill
 * condition, firing {@code milestones} as it climbs and running {@code onFull} at the top. The
 * quest-specific fill/effect/action implementations are named Java functions (see RegionalQuestManager).
 */
public record ActivationProfile(
        int radius,
        int fillUp,
        int fillDown,
        int threshold,
        int nearbyPrey,
        int discoveryRadius,
        Optional<ResourceLocation> biomeTag,
        List<ActivationMilestone> milestones,
        ActivationCompletion onFull
) {
    public ActivationProfile {
        if(radius<=0)throw new IllegalArgumentException("activation radius must be positive");
        if(threshold<=0)throw new IllegalArgumentException("activation threshold must be positive");
        milestones = milestones==null ? List.of() : List.copyOf(milestones);
        if(biomeTag==null)biomeTag=Optional.empty();
    }
}
