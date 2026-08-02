package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

public record SettlementExpansionPlan(
        ResourceLocation templateId,
        StructureRole role,
        BlockPos origin,
        Rotation rotation
) {
}