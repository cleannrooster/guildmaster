package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.detection.AnchorGenerator;
import dev.campaigncore.settlers.detection.StructureClassifier;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Optional;

public final class SettlementExpander {
    private SettlementExpander() {
    }

    public static SettlementExpansionResult expand(
            ServerLevel level,
            ServerPlayer player,
            Settlement settlement,
            StructureRole requestedRole
    ) {
        Optional<ResourceLocation> optionalTemplate =
                SettlementTemplateSelector.select(
                        settlement,
                        requestedRole,
                        level.getRandom()
                );

        if (optionalTemplate.isEmpty()) {
            return SettlementExpansionResult.failure(
                    "Settlement '"
                            + settlement.name()
                            + "' has no existing "
                            + requestedRole.getSerializedName()
                            + " template that can be reproduced."
            );
        }

        ResourceLocation templateId =
                optionalTemplate.get();

        Optional<SettlementExpansionPlan> optionalPlan =
                SettlementExpansionPlanner.planInFrontOfPlayer(
                        level,
                        player,
                        templateId,
                        requestedRole,
                        6
                );

        if (optionalPlan.isEmpty()) {
            return SettlementExpansionResult.failure(
                    "The settlement could not find a valid site "
                            + "for another "
                            + requestedRole.getSerializedName()
                            + "."
            );
        }

        SettlementExpansionPlan plan =
                optionalPlan.get();

        RuntimeTemplateResult generated =
                RuntimeTemplatePlacer.place(
                        level,
                        player,
                        settlement,
                        plan
                );

        if (!generated.success()) {
            return SettlementExpansionResult.failure(
                    generated.message()
            );
        }

        StructureAssignment assignment =
                new StructureAssignment(
                        templateId,
                        requestedRole,
                        generated.bounds()
                );

        List<StructureAssignment> assignments =
                List.of(assignment);

        settlement.addStructures(assignments);
        settlement.recordChronicle(level.getGameTime(), "expanded", requestedRole.getSerializedName());

        // The new building's destination chunks are guaranteed loaded by RuntimeTemplatePlacer. Add
        // its anchors immediately even when some older, distant settlement chunk is unloaded and a
        // complete atomic rebuild must be postponed.
        AnchorGenerator.addForStructures(level, settlement.anchors(), assignments);

        settlement.markAnchorsDirty(level.getGameTime()
                + dev.campaigncore.settlers.settlement.SettlementAnchorUpdater.DIRTY_DEBOUNCE_TICKS);

        // Runtime template placement is synchronous and its destination chunks were validated above,
        // so rebuild immediately for command-visible housing/work capacity. If an older registered
        // structure is currently unloaded, rebuild() leaves the complete old table intact and the
        // dirty flag schedules a retry instead.
        if (dev.campaigncore.settlers.settlement.SettlementAnchorUpdater.rebuild(level, settlement)) {
            settlement.completeAnchorRebuild(level.getGameTime()
                    + dev.campaigncore.settlers.settlement.SettlementAnchorUpdater.AUDIT_INTERVAL_TICKS);
        }

        SettlementManager.get(level).markDirty();

        return SettlementExpansionResult.success(
                templateId,
                plan.origin(),
                assignments
        );
    }
}
