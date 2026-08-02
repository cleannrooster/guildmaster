package dev.campaigncore.settlers.expansion;

import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

public final class SettlementExpansionPlanner {
    private SettlementExpansionPlanner() {
    }

    public static Optional<SettlementExpansionPlan> planInFrontOfPlayer(
            ServerLevel level,
            ServerPlayer player,
            ResourceLocation templateId,
            StructureRole role,
            int distance
    ) {
        Optional<StructureTemplate> optionalTemplate =
                level.getStructureManager().get(templateId);

        if (optionalTemplate.isEmpty()) {
            return Optional.empty();
        }

        StructureTemplate template = optionalTemplate.get();

        Direction playerFacing = player.getDirection();

        /*
         * The building faces toward the player, so its front face points in the
         * opposite direction from the direction the player is looking.
         *
         * Player looks north:
         * building is north of player;
         * building front faces south, toward player.
         */
        Direction buildingFront = playerFacing.getOpposite();

        Rotation rotation = rotationForFront(buildingFront);

        Vec3i rotatedSize =
                template.getSize(rotation);

        int width = rotatedSize.getX();
        int depth = rotatedSize.getZ();

        /*
         * Position representing the middle of the building's front face.
         * It is placed `distance` blocks ahead of the player's feet.
         */

        BlockPos horizontalTarget = player.blockPosition()
                .relative(playerFacing, distance);

        BlockPos frontCenter = level.getHeightmapPos(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                horizontalTarget
        );
        BlockPos origin = originFromFrontCenter(
                frontCenter,
                buildingFront,
                width,
                depth
        );

        return Optional.of(
                new SettlementExpansionPlan(
                        templateId,
                        role,
                        origin,
                        rotation
                )
        );
    }
    private static Rotation rotationForFront(
            Direction buildingFront
    ) {
        /*
         * Assumption: the unrotated template's front points south.
         *
         * Adjust this mapping if your authored templates use a different
         * canonical front direction.
         */
        return switch (buildingFront) {
            case SOUTH -> Rotation.NONE;
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
    private static BlockPos originFromFrontCenter(
            BlockPos frontCenter,
            Direction buildingFront,
            int width,
            int depth
    ) {
        /*
         * The origin is the minimum X/Z corner of the rotated template.
         *
         * Integer division means even-width buildings will be offset by half a
         * block to one side, which is unavoidable when aligning block coordinates.
         */
        int halfWidth = width / 2;

        return switch (buildingFront) {
            /*
             * Front face is on maximum Z.
             * Building extends north from the front face.
             */
            case SOUTH -> new BlockPos(
                    frontCenter.getX() - halfWidth ,
                    frontCenter.getY(),
                    frontCenter.getZ() -depth/2
            );

            /*
             * Front face is on minimum Z.
             * Building extends south from the front face.
             */
            case NORTH -> new BlockPos(
                    frontCenter.getX() + halfWidth,
                    frontCenter.getY(),
                    frontCenter.getZ() + depth/2
            );

            /*
             * Front face is on maximum X.
             * Building extends west from the front face.
             */
            case EAST -> new BlockPos(
                    frontCenter.getX() - halfWidth,
                    frontCenter.getY(),
                    frontCenter.getZ() + depth/2
            );

            /*
             * Front face is on minimum X.
             * Building extends east from the front face.
             */
            case WEST -> new BlockPos(
                    frontCenter.getX() + halfWidth,
                    frontCenter.getY(),
                    frontCenter.getZ() - depth/2
            );

            default -> frontCenter;
        };
    }
}