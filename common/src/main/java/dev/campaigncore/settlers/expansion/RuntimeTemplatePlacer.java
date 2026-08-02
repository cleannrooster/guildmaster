package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/// Loads and places one existing structure template at runtime.
///
/// This is deliberately separate from settlement planning. The planner chooses
/// the template, origin, and rotation; this class validates and performs the
/// irreversible world modification.
public final class RuntimeTemplatePlacer {
    private static final int PLACEMENT_FLAGS = 2;
    private static final int COLLISION_MARGIN = 1;

    private RuntimeTemplatePlacer() {
    }

    public static RuntimeTemplateResult place(
            ServerLevel level,
            Player player,
            Settlement settlement,
            SettlementExpansionPlan plan
    ) {
        Optional<StructureTemplate> optionalTemplate =
                level.getStructureManager().get(plan.templateId());

        if (optionalTemplate.isEmpty()) {
            return RuntimeTemplateResult.failure(
                    "Structure template "
                            + plan.templateId()
                            + " could not be found."
            );
        }

        StructureTemplate template = optionalTemplate.get();

        StructurePlaceSettings settings =
                createPlacementSettings(plan);

        /*
         * plan.origin() is the template's transformed minimum placement
         * position, not its center.
         */
        BoundingBox bounds =
                template.getBoundingBox(
                        settings,
                        plan.origin()
                );

        if (!areDestinationChunksLoaded(level, bounds)) {
            return RuntimeTemplateResult.failure(
                    "The proposed expansion site crosses unloaded chunks."
            );
        }

        if (collidesWithSettlement(settlement, bounds)) {
            return RuntimeTemplateResult.failure(
                    "The proposed building overlaps an existing "
                            + "settlement structure."
            );
        }

        if (!hasReasonableVerticalPosition(level, bounds)) {
            return RuntimeTemplateResult.failure(
                    "The proposed building extends outside the world's "
                            + "build-height limits."
            );
        }

        /*
         * The third BlockPos is the transformation pivot used internally
         * during placement. BlockPos.ZERO matches the zero rotation pivot
         * configured in createPlacementSettings().
         */
        boolean placed = template.placeInWorld(
                level,
                plan.origin(),
                BlockPos.ZERO,
                settings,
                level.getRandom(),
                PLACEMENT_FLAGS
        );

        if (!placed) {
            return RuntimeTemplateResult.failure(
                    "Minecraft rejected placement of template "
                            + plan.templateId()
                            + "."
            );
        }

        return RuntimeTemplateResult.success(bounds);
    }

    private static StructurePlaceSettings createPlacementSettings(
            SettlementExpansionPlan plan
    ) {
        return new StructurePlaceSettings()
                .setMirror(Mirror.NONE)
                .setRotation(plan.rotation())
                .setRotationPivot(BlockPos.ZERO)

                /*
                 * Structure blocks are authoring metadata and should not
                 * appear in the generated building.
                 */
                .addProcessor(
                        BlockIgnoreProcessor.STRUCTURE_BLOCK
                )

                /*
                 * Village templates commonly contain jigsaw blocks. This
                 * processor replaces each one with the block encoded in its
                 * final_state property.
                 */
                .addProcessor(
                        JigsawReplacementProcessor.INSTANCE
                );
    }

    private static boolean areDestinationChunksLoaded(
            ServerLevel level,
            BoundingBox bounds
    ) {
        return level.hasChunksAt(
                bounds.minX(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxZ()
        );
    }

    private static boolean hasReasonableVerticalPosition(
            ServerLevel level,
            BoundingBox bounds
    ) {
        return bounds.minY() >= level.getMinBuildHeight()
                && bounds.maxY() < level.getMaxBuildHeight();
    }

    private static boolean collidesWithSettlement(
            Settlement settlement,
            BoundingBox proposed
    ) {
        BoundingBox padded =
                inflated(proposed, COLLISION_MARGIN);

        for (StructureAssignment existing
                : settlement.structures()) {
            if (padded.intersects(existing.bounds())) {
                return true;
            }
        }

        return false;
    }

    private static BoundingBox inflated(
            BoundingBox box,
            int amount
    ) {
        return new BoundingBox(
                box.minX() - amount,
                box.minY() - amount,
                box.minZ() - amount,
                box.maxX() + amount,
                box.maxY() + amount,
                box.maxZ() + amount
        );
    }
}