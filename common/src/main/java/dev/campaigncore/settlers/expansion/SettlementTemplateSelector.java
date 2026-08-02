package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SettlementTemplateSelector {
    private static final ResourceLocation UNKNOWN_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    SettlersMod.MOD_ID,
                    "unknown_piece"
            );

    private SettlementTemplateSelector() {
    }

    public static List<WeightedTemplate> templatesFor(
            Settlement settlement,
            StructureRole requestedRole
    ) {
        Map<ResourceLocation, Integer> counts =
                new LinkedHashMap<>();

        for (StructureAssignment assignment
                : settlement.structures()) {
            if (assignment.role() != requestedRole) {
                continue;
            }

            ResourceLocation template =
                    assignment.template();

            if (template.equals(UNKNOWN_TEMPLATE)) {
                continue;
            }

            counts.merge(template, 1, Integer::sum);
        }

        return counts.entrySet()
                .stream()
                .map(entry -> new WeightedTemplate(
                        entry.getKey(),
                        entry.getValue()
                ))
                .toList();
    }

    public static Optional<ResourceLocation> select(
            Settlement settlement,
            StructureRole requestedRole,
            RandomSource random
    ) {
        List<WeightedTemplate> templates =
                templatesFor(settlement, requestedRole);

        int totalWeight = templates.stream()
                .mapToInt(WeightedTemplate::weight)
                .sum();

        if (totalWeight <= 0) {
            return Optional.empty();
        }

        int roll = random.nextInt(totalWeight);

        for (WeightedTemplate template : templates) {
            roll -= template.weight();

            if (roll < 0) {
                return Optional.of(template.template());
            }
        }

        return Optional.empty();
    }

    public record WeightedTemplate(
            ResourceLocation template,
            int weight
    ) {
        public WeightedTemplate {
            if (weight < 1) {
                throw new IllegalArgumentException(
                        "Template weight must be positive."
                );
            }
        }
    }
}