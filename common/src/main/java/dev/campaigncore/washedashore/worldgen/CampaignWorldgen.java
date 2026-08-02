package dev.campaigncore.washedashore.worldgen;

import dev.architectury.registry.level.biome.BiomeModifications;
import dev.architectury.registry.registries.*;
import dev.campaigncore.CampaignCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class CampaignWorldgen {
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(CampaignCore.MOD_ID,Registries.FEATURE);
    public static final RegistrySupplier<Feature<NoneFeatureConfiguration>> STORYLINE_ANCHOR=FEATURES.register("storyline_anchor",
            ()->new StorylineAnchorFeature(NoneFeatureConfiguration.CODEC));
    private static final ResourceKey<PlacedFeature> PLACED=ResourceKey.create(Registries.PLACED_FEATURE,CampaignCore.id("storyline_anchor"));
    private CampaignWorldgen(){}
    public static void register(){
        FEATURES.register();
        BiomeModifications.addProperties(ctx->ctx.hasTag(BiomeTags.IS_BEACH),(ctx,properties)->
                properties.getGenerationProperties().addFeature(GenerationStep.Decoration.LOCAL_MODIFICATIONS,PLACED));
    }
}
