package dev.campaigncore.settlers.production;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.settlement.AnchorType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/// The hard-coded catalog of production processes for this pass. Deliberately in-code (no JSON reload
/// yet): the first slice ships food from the three agricultural professions. A generic
/// {@link AnchorType#WORK} process is intentionally absent so nothing produces from an unrelated
/// workstation.
public final class ProductionProcesses {
    private static final int ONE_MINUTE_TICKS = 1200;
    /// Twenty one-minute cycles occur per Minecraft day, so 0.6 food per cycle is twelve per worker/day.
    private static final double FOOD_PER_WORKER_CYCLE = 0.6;

    private static final List<ProductionProcess> ALL = List.of(
            new ProductionProcess(id("field_labor"), "farmer", AnchorType.CROP_TENDING, ONE_MINUTE_TICKS,
                    Map.of(SettlementResources.FOOD, FOOD_PER_WORKER_CYCLE)),
            new ProductionProcess(id("animal_tending"), "herder", AnchorType.ANIMAL_TENDING, ONE_MINUTE_TICKS,
                    Map.of(SettlementResources.FOOD, FOOD_PER_WORKER_CYCLE, SettlementResources.ANIMAL_GOODS, 0.25)),
            new ProductionProcess(id("fishing"), "fisher", AnchorType.FISHING, ONE_MINUTE_TICKS,
                    Map.of(SettlementResources.FOOD, FOOD_PER_WORKER_CYCLE, SettlementResources.FISH, 0.25)));

    private ProductionProcesses() {
    }

    public static List<ProductionProcess> all() {
        return ALL;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, path);
    }
}
