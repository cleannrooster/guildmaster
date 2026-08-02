package dev.campaigncore.washedashore.mixin;

import dev.campaigncore.washedashore.encounter.CampaignSpawnProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents campaign-spawned undead from passing vanilla's daylight ignition check. */
@Mixin(Mob.class)
public abstract class SunProtectedMobMixin {
    @Inject(method="isSunBurnTick",at=@At("HEAD"),cancellable=true)
    private void campaignCore$preventSunBurn(CallbackInfoReturnable<Boolean> cir){
        if(CampaignSpawnProtection.isProtectedFromSun((Entity)(Object)this))cir.setReturnValue(false);
    }
}
