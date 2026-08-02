package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.settlement.Settlement;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SettlementBuildingPoolRegistry {
    private static final Map<String, ResourceLocation> MINECRAFT_POOLS =
            Map.of(
                    "residence",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "farm",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "workshop",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    )
            );
    private static final Map<String, ResourceLocation> CTOV_POOLS =
            Map.of(
                    "residence",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "farm",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "workshop",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    )
            );
    private static final Map<String, ResourceLocation> TOT_POOLS =
            Map.of(
                    "residence",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "farm",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    ),

                    "workshop",
                    ResourceLocation.withDefaultNamespace(
                            "village/plains/houses"
                    )
            );
    private SettlementBuildingPoolRegistry() {
    }

    public static Optional<ResourceLocation> resolve(
            Settlement settlement,
            String requestedType
    ) {
        String type = requestedType
                .toLowerCase(Locale.ROOT)
                .trim();

        String namespace =
                settlement.profile().sourceVillageType();

        /*
         * Only provide mappings that have been tested. Unknown modded village
         * types should not silently use an incompatible pool, so they resolve
         * to empty.
         */
        Map<String, ResourceLocation> pools = switch (namespace) {
            case "minecraft" -> MINECRAFT_POOLS;
            case "ctov" -> CTOV_POOLS;
            case "t_and_t", "towns_and_towers" -> TOT_POOLS;
            default -> Map.of();
        };

        return Optional.ofNullable(pools.get(type));
    }
}