package dev.campaigncore.settlers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AbstractSkullBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.ItemArmorGeoLayer;
import software.bernie.geckolib.util.Color;

/// Renders Settlers with three layers on top of the geo model:
/// - SettlerJobOverlayLayer paints the job-identifying overlay (hat/jacket/sleeves/pant-legs regions)
///   on top of the settler's random base skin;
/// - ItemArmorGeoLayer fits vanilla-pipeline armor (any player-proportioned set, including modded)
///   onto the rig's bones — the whole point of the human-compatible rig;
/// - BlockAndItemGeoLayer renders held items at the hand locator bones.
public class SettlerRenderer extends GeoEntityRenderer<SettlerEntity> {

    public SettlerRenderer(EntityRendererProvider.Context context) {
        super(context, new SettlerModel());
        this.shadowRadius = 0.5f;

        addRenderLayer(new SettlerJobOverlayLayer(this));

        addRenderLayer(new ItemArmorGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getArmorItemForBone(
                    GeoBone bone,
                    SettlerEntity animatable
            ) {
                return switch (bone.getName()) {
                    case "head" ->
                            this.helmetStack;

                    case "body", "rightArm", "leftArm" ->
                            this.chestplateStack;

                    case "armorWaist", "rightLeg", "leftLeg" ->
                            this.leggingsStack;

                    case "rightBoot", "leftBoot" ->
                            this.bootsStack;

                    default ->
                            null;
                };
            }

            @Override
            protected EquipmentSlot getEquipmentSlotForBone(
                    GeoBone bone,
                    ItemStack stack,
                    SettlerEntity animatable
            ) {
                return switch (bone.getName()) {
                    case "head" ->
                            EquipmentSlot.HEAD;

                    case "body", "rightArm", "leftArm" ->
                            EquipmentSlot.CHEST;

                    case "armorWaist", "rightLeg", "leftLeg" ->
                            EquipmentSlot.LEGS;

                    case "rightBoot", "leftBoot" ->
                            EquipmentSlot.FEET;

                    default ->
                            super.getEquipmentSlotForBone(
                                    bone,
                                    stack,
                                    animatable
                            );
                };
            }

            @Override
            protected ModelPart getModelPartForBone(
                    GeoBone bone,
                    EquipmentSlot slot,
                    ItemStack stack,
                    SettlerEntity animatable,
                    HumanoidModel<?> baseModel
            ) {
                return switch (bone.getName()) {
                    case "head" ->
                            baseModel.head;

                    case "body", "armorWaist" ->
                            baseModel.body;

                    case "rightArm" ->
                            baseModel.rightArm;

                    case "leftArm" ->
                            baseModel.leftArm;

                    case "rightLeg", "rightBoot" ->
                            baseModel.rightLeg;

                    case "leftLeg", "leftBoot" ->
                            baseModel.leftLeg;

                    default ->
                            super.getModelPartForBone(
                                    bone,
                                    slot,
                                    stack,
                                    animatable,
                                    baseModel
                            );
                };
            }

            @Override
            protected void prepModelPartForRender(
                    PoseStack poseStack,
                    GeoBone bone,
                    ModelPart sourcePart
            ) {
                /*
                 * The pose stack has already inherited this GeoBone's transform.
                 *
                 * ItemArmorGeoLayer's default implementation also copies the
                 * GeoBone's local rotation onto the vanilla ModelPart. Temporarily
                 * zeroing the local rotation prevents that second application while
                 * preserving GeckoLib's position and scale preparation.
                 */
                float rotX = bone.getRotX();
                float rotY = bone.getRotY();
                float rotZ = bone.getRotZ();

                bone.setRotX(0.0f);
                bone.setRotY(0.0f);
                bone.setRotZ(0.0f);

                try {
                    super.prepModelPartForRender(
                            poseStack,
                            bone,
                            sourcePart
                    );
                } finally {
                    bone.setRotX(rotX);
                    bone.setRotY(rotY);
                    bone.setRotZ(rotZ);
                }
            }
        });
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getStackForBone(GeoBone bone, SettlerEntity animatable) {
                return switch (bone.getName()) {
                    case "rightItem" -> animatable.getMainHandItem();
                    case "leftItem" -> animatable.getOffhandItem();
                    default -> null;
                };
            }

            @Override
            public void render(PoseStack poseStack, SettlerEntity animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {

                super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, SettlerEntity animatable, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();
                poseStack.mulPose(Axis.XP.rotationDegrees(-90));

                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
                poseStack.popPose();

            }

            @Override
            public void preRender(PoseStack poseStack, SettlerEntity animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                super.preRender(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, SettlerEntity animatable) {
                // GeckoLib's default renderStackForBone hands this straight to vanilla's
                // ItemInHandRenderer with the current poseStack (already in the hand bone's local
                // space via prepMatrixForBone) — the THIRD_PERSON_*_HAND display transform baked into
                // the item model is sufficient on its own. An earlier version of this layer added an
                // extra manual -90deg X rotation on top "to match vanilla third-person holding", which
                // instead double-transformed the item, most visibly on the off-hand. Removed.
                return bone.getName().equals("leftItem")
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }
        });
    }

}
