package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.settlement.AnchorType;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/// One abstract production recipe: workers of {@code profession}, assigned to a {@code workstationType}
/// anchor, complete one {@code cycleTicks}-long cycle to yield {@code outputs} (abstract-resource id →
/// amount per cycle). Keyed on both profession and anchor type so a worker who fell back to a generic
/// {@link AnchorType#WORK} anchor never drives a farm/herd/fishing process.
public record ProductionProcess(
        ResourceLocation id,
        String profession,
        AnchorType workstationType,
        int cycleTicks,
        Map<ResourceLocation, Double> outputs
) {
    public ProductionProcess {
        outputs = Map.copyOf(outputs);
    }
}
