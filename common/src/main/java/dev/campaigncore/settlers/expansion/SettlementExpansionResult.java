package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.settlement.StructureAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public record SettlementExpansionResult(
        boolean success,
        String message,
        ResourceLocation templateId,
        BlockPos origin,
        List<StructureAssignment> assignments
) {
    public SettlementExpansionResult {
        message = message == null ? "" : message;
        assignments = assignments == null
                ? List.of()
                : List.copyOf(assignments);
    }

    public static SettlementExpansionResult failure(String message) {
        return new SettlementExpansionResult(
                false,
                message,
                null,
                null,
                List.of()
        );
    }

    public static SettlementExpansionResult success(
            ResourceLocation templateId,
            BlockPos origin,
            List<StructureAssignment> assignments
    ) {
        return new SettlementExpansionResult(
                true,
                "Expansion generated successfully.",
                templateId,
                origin,
                assignments
        );
    }
}