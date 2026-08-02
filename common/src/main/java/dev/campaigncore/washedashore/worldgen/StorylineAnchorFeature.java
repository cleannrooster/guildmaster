package dev.campaigncore.washedashore.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** Tiny beach landmark; campaign creation is deliberately deferred to normal server logic. */
public final class StorylineAnchorFeature extends Feature<NoneFeatureConfiguration> {
    public StorylineAnchorFeature(Codec<NoneFeatureConfiguration> codec){super(codec);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context){
        WorldGenLevel level=context.level();BlockPos origin=context.origin();
        if(!level.getBiome(origin).is(BiomeTags.IS_BEACH))return false;
        BlockPos ground=origin;
        while(ground.getY()>level.getMinBuildHeight()+1&&level.getBlockState(ground).isAir())ground=ground.below();
        if(level.getBlockState(ground).getFluidState().isSource())return false;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)level.setBlock(ground.offset(x,0,z),Blocks.SMOOTH_SANDSTONE.defaultBlockState(),2);
        level.setBlock(ground.above(),Blocks.CHISELED_SANDSTONE.defaultBlockState(),2);
        level.setBlock(ground.above(2),Blocks.LODESTONE.defaultBlockState(),2);
        return true;
    }
}
