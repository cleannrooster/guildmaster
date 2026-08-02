package dev.campaigncore.settlers.mixin;

import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(NearestAttackableTargetGoal.class)
abstract class NearestAttackableTargetGoalMixin<T extends LivingEntity> extends TargetGoal {
    @Shadow protected Class<T> targetType;
    @Shadow protected LivingEntity target;
    @Shadow protected TargetingConditions targetConditions;

    public NearestAttackableTargetGoalMixin(Mob mob, boolean mustSee) {
        super(mob, mustSee);
    }

    @Shadow protected abstract AABB getTargetSearchArea(double distance);

    @Inject(method = "findTarget", at = @At("HEAD"), cancellable = true)
    private void settlers$includeSettlersInVillagerTargeting(CallbackInfo callback) {
        if (targetType != Villager.class && targetType != AbstractVillager.class) {
            return;
        }
        List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                LivingEntity.class,
                getTargetSearchArea(this.getFollowDistance()),
                candidate -> targetType.isInstance(candidate) || candidate instanceof SettlerEntity
        );
        target = mob.level().getNearestEntity(
                candidates,
                targetConditions,
                mob,
                mob.getX(),
                mob.getEyeY(),
                mob.getZ()
        );
        callback.cancel();
    }
}
