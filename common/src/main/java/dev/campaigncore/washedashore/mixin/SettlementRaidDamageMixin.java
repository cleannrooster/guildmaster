package dev.campaigncore.washedashore.mixin;

import dev.campaigncore.washedashore.act.SettlementRaidManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Settlement raiders (tagged by {@link SettlementRaidManager}) take only 25% damage from Settlers
 * mod townsfolk and their guards, so the town's defenders wear them down slowly rather than deleting
 * them — the player is meant to be the one who finishes the raid.
 */
@Mixin(LivingEntity.class)
public abstract class SettlementRaidDamageMixin {
    @ModifyVariable(method="hurt",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private float campaignCore$softenSettlerDamageToRaiders(float amount,DamageSource source){
        Entity attacker=source.getEntity();
        return attacker!=null
                &&((LivingEntity)(Object)this).getTags().contains(SettlementRaidManager.RAID_MEMBER_TAG)
                &&isSettler(attacker)
                ?amount*0.25f:amount;
    }
    private static boolean isSettler(Entity attacker){
        var key=BuiltInRegistries.ENTITY_TYPE.getKey(attacker.getType());
        return key!=null&&key.getNamespace().equals("settlers");
    }
}
