package dev.campaigncore.washedashore.mixin;

import dev.campaigncore.compat.LegacyCampaignCompatibility;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class TutorialBossDamageMixin {
    @ModifyVariable(method="hurt",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float campaignCore$scaleTutorialBossDamage(float amount,DamageSource source){
        return source.getEntity()!=null&&(source.getEntity().getTags().contains("campaign_core_washed_ashore_tutorial_boss")
                ||source.getEntity().getTags().contains(LegacyCampaignCompatibility.TUTORIAL_BOSS_TAG))
                ?amount*.5f:amount;
    }
}
