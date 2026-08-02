package dev.campaigncore.settlers.expansion;

import com.mojang.datafixers.util.Pair;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class DynamicSettlementPoolFactory {
    private DynamicSettlementPoolFactory() {
    }

    public static Optional<StructureTemplatePool> create(
            RegistryAccess registries,
            Settlement settlement,
            StructureRole role
    ) {
        var templates =
                SettlementTemplateSelector.templatesFor(
                        settlement,
                        role
                );

        if (templates.isEmpty()) {
            return Optional.empty();
        }

        List<Pair<StructurePoolElement, Integer>> elements =
                new ArrayList<>();

        for (var weighted : templates) {
            /*
             * Adjust this factory call to the exact 1.21.1
             * Mojmap/Parchment signature in your workspace.
             */
            StructurePoolElement element =
                    SinglePoolElement.single(
                            weighted.template().toString()
                    ).apply(
                            StructureTemplatePool.Projection.RIGID
                    );

            elements.add(
                    Pair.of(element, weighted.weight())
            );
        }

        Holder<StructureTemplatePool> emptyFallback =
                registries
                        .registryOrThrow(
                                Registries.TEMPLATE_POOL
                        )
                        .getHolderOrThrow(
                                net.minecraft.resources.ResourceKey.create(
                                        Registries.TEMPLATE_POOL,
                                        ResourceLocation.withDefaultNamespace(
                                                "empty"
                                        )
                                )
                        );

        return Optional.of(
                new StructureTemplatePool(
                        emptyFallback,
                        elements
                )
        );
    }
}