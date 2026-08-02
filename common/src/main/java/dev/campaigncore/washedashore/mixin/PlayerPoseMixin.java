package dev.campaigncore.washedashore.mixin;

import dev.campaigncore.washedashore.recovery.ProneCondition;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerPoseMixin {
    @Inject(method="updateSwimming",at=@At("HEAD"),cancellable=true)
    private void campaignCore$preserveProneSwimmingFlag(CallbackInfo ci){
        Player player=(Player)(Object)this;
        if(ProneCondition.shouldForcePose(player)){
            player.setSwimming(true);
            ci.cancel();
        }
    }

    @Inject(method="updatePlayerPose",at=@At("HEAD"),cancellable=true)
    private void campaignCore$forcePronePose(CallbackInfo ci){
        Player player=(Player)(Object)this;
        if(ProneCondition.shouldForcePose(player)){
            player.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }

}
