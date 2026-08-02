package dev.campaigncore.settlers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/// Renders a job-identifying overlay on top of a Settler's random base skin: a full re-render of the
/// same baked geometry using the profile's {@code job_overlay} texture, which is fully transparent
/// except for the vanilla skin format's own second-layer regions (hat/jacket/sleeves/pant-legs) — the
/// same mechanism vanilla skins already use for their own overlay layer, so this reads correctly
/// regardless of which random base skin the settler has. Modeled directly on GeckoLib's own
/// AutoGlowingGeoLayer (same re-render technique, minus the full-bright/emissive render type — this
/// is a normal alpha-blended pass at the entity's actual light level).
public class SettlerJobOverlayLayer extends GeoRenderLayer<SettlerEntity> {
    public SettlerJobOverlayLayer(GeoRenderer<SettlerEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, SettlerEntity animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType,
                       MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                       int packedLight, int packedOverlay) {
        ResourceLocation overlay = animatable.jobOverlayTexture().orElse(null);
        if (overlay == null) {
            return;
        }
        RenderType overlayRenderType = RenderType.entityTranslucent(overlay);
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, overlayRenderType,
                bufferSource.getBuffer(overlayRenderType), partialTick, packedLight, packedOverlay,
                getRenderer().getRenderColor(animatable, partialTick, packedLight).argbInt());
    }
}
