package dev.campaigncore.settlers.client.render;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

/// Player-proportioned humanoid rig. Locomotion is procedural — vanilla's exact limb-swing formulas
/// driven by the entity's WalkAnimationState — so movement always matches actual velocity and armor
/// (which copies bone rotations) stays glued. Authored clips on the "base" controller layer on top
/// for action animations.
public class SettlerModel extends GeoModel<SettlerEntity> {
    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "geo/settler.geo.json");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "animations/settler.animation.json");
    private static final String[] ARMOR_REFERENCE_BONES = {
            "armorHead",
            "armorBody",
            "armorWaist",
            "armorRightArm",
            "armorLeftArm",
            "armorRightLeg",
            "armorLeftLeg",
            "armorRightBoot",
            "armorLeftBoot"
    };
    @Override
    public ResourceLocation getModelResource(SettlerEntity animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getAnimationResource(SettlerEntity animatable) {
        return ANIMATION;
    }

    @Override
    public ResourceLocation getTextureResource(SettlerEntity animatable) {
        // Synced from the server-side profile; any player-format (64x64) skin works.
        return animatable.textureLocation();
    }

    @Override
    public void setCustomAnimations(SettlerEntity animatable, long instanceId, AnimationState<SettlerEntity> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        float partialTick = animationState.getPartialTick();
        float limbSwing = animatable.walkAnimation.position(partialTick);
        float limbSwingAmount = Math.min(animatable.walkAnimation.speed(partialTick), 1.0f);
        float ageInTicks = animatable.tickCount + partialTick;

        // Head tracking (vanilla convention, same approach as other geo humanoids on this rig).
        this.getBone("head").ifPresent(head -> {
            var data = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
            head.setRotY(data.netHeadYaw() * Mth.DEG_TO_RAD);
            head.setRotX(data.headPitch() * Mth.DEG_TO_RAD);
        });

        // Vanilla HumanoidModel gait, verbatim.
        float rightArmSwing = Mth.cos(limbSwing * 0.6662f + Mth.PI) * 2.0f * limbSwingAmount * 0.5f;
        float leftArmSwing = Mth.cos(limbSwing * 0.6662f) * 2.0f * limbSwingAmount * 0.5f;
        float rightLegSwing = Mth.cos(limbSwing * 0.6662f) * 1.4f * limbSwingAmount;
        float leftLegSwing = Mth.cos(limbSwing * 0.6662f + Mth.PI) * 1.4f * limbSwingAmount;
        // Vanilla idle arm bob (AnimationUtils.bobModelPart).
        float idleZ = Mth.cos(ageInTicks * 0.09f) * 0.05f + 0.05f;
        float idleX = Mth.sin(ageInTicks * 0.067f) * 0.05f;

        this.getBone("rightArm").ifPresent(bone -> {
            bone.setRotX(rightArmSwing + idleX);
            bone.setRotZ(idleZ);
        });
        this.getBone("leftArm").ifPresent(bone -> {
            bone.setRotX(leftArmSwing - idleX);
            bone.setRotZ(-idleZ);
        });
        this.getBone("rightLeg").ifPresent(bone -> bone.setRotX(rightLegSwing));
        this.getBone("leftLeg").ifPresent(bone -> bone.setRotX(leftLegSwing));

        // Boot bones exist purely as armor-scaling references for ItemArmorGeoLayer (which requires a
        // cube on every armor-target bone); their cubes must never draw.
        this.getBone("rightBoot").ifPresent(bone -> bone.setHidden(true));
        this.getBone("leftBoot").ifPresent(bone -> bone.setHidden(true));

    }

    /// Authored clips may animate a subset of bones; skip missing channels instead of crashing.
    @Override
    public boolean crashIfBoneMissing() {
        return false;
    }
}
