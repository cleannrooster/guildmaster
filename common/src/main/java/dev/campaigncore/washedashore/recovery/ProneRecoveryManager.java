package dev.campaigncore.washedashore.recovery;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.act.WashedAshoreProgress;
import dev.campaigncore.washedashore.config.WashedAshoreConfig;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import dev.campaigncore.washedashore.message.CampaignMessages;

public final class ProneRecoveryManager {
    public static final int FIRST_AWAKENING_TICKS=80,DEATH_RESPAWN_TICKS=40;
    private static final ResourceLocation SPEED_MODIFIER=CampaignCore.washedAshoreId("prone_recovery_speed");
    private ProneRecoveryManager(){}

    public static int requiredRecoveryTicks(ServerPlayer player,ProneRecoveryReason reason){
        var c=WashedAshoreConfig.INSTANCE;
        return switch(reason){case FIRST_AWAKENING->Math.max(1,c.firstAwakeningMovementTicks);
            case DEATH_RESPAWN->Math.max(1,c.deathRespawnMovementTicks);case SCRIPTED->DEATH_RESPAWN_TICKS;};
    }
    public static boolean beginRecovery(ServerPlayer player,ProneRecoveryReason reason,int requiredTicks){
        return beginRecovery(player,reason,requiredTicks,false);
    }
    public static boolean beginRecovery(ServerPlayer player,ProneRecoveryReason reason,int requiredTicks,boolean force){
        WashedAshoreSavedData saved=WashedAshoreSavedData.get(player.serverLevel());ProneRecoveryData data=saved.player(player.getUUID()).proneRecovery();
        if(data.active()&&!force)return false;
        if(player.isInWater())relocateFromWater(player);
        int protection=reason==ProneRecoveryReason.DEATH_RESPAWN?Math.min(
                WashedAshoreConfig.INSTANCE.maximumRespawnProtectionTicks,WashedAshoreConfig.INSTANCE.respawnProtectionTicks):20;
        data.begin(reason,Math.max(1,requiredTicks),player.position(),protection);
        applyRestrictions(player,data);saved.dirty();
        CampaignCore.LOGGER.debug("prone_recovery_started player={} reason={} required={} protection={}",player.getUUID(),reason,requiredTicks,protection);
        return true;
    }
    public static boolean beginFirstAwakening(ServerPlayer player){
        ProneRecoveryData data=WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID()).proneRecovery();
        if(data.firstAwakeningComplete())return false;
        CampaignCore.LOGGER.debug("first_awakening_invoked player={}",player.getUUID());
        return beginRecovery(player,ProneRecoveryReason.FIRST_AWAKENING,requiredRecoveryTicks(player,ProneRecoveryReason.FIRST_AWAKENING));
    }
    public static void beginDeathRecovery(ServerPlayer player){
        beginRecovery(player,ProneRecoveryReason.DEATH_RESPAWN,requiredRecoveryTicks(player,ProneRecoveryReason.DEATH_RESPAWN),true);
        CampaignCore.LOGGER.debug("death_respawn_recovery_hook player={}",player.getUUID());
    }
    public static void tick(ServerLevel level){
        for(ServerPlayer player:level.players()){
            WashedAshoreSavedData saved=WashedAshoreSavedData.get(level);ProneRecoveryData data=saved.player(player.getUUID()).proneRecovery();
            if(!data.active())continue;
            if(!ProneCondition.isApplied(player)){ProneCondition.apply(player);CampaignCore.LOGGER.debug("missing_prone_condition_restored player={}",player.getUUID());}
            applyRestrictions(player,data);
            Vec3 previous=data.previousPosition(),current=player.position();
            if(previous==null){data.setPreviousPosition(current);saved.dirty();continue;}
            double dx=current.x-previous.x,dz=current.z-previous.z,horizontal=dx*dx+dz*dz;
            double minimum=Math.max(.001,WashedAshoreConfig.INSTANCE.minimumMovementDistance);
            boolean teleport=horizontal>1.0;
            boolean safeIntent=player.onGround()&&!player.isInWater()&&!player.isPassenger()&&player.hurtTime==0
                    &&player.fallDistance<.5f&&!teleport;
            boolean moved=safeIntent&&horizontal>=minimum*minimum;
            if(moved){
                data.setGrace(Math.max(0,WashedAshoreConfig.INSTANCE.obstructionGraceTicks));data.increment();data.setState(ProneRecoveryState.RECOVERING);
            }else if(safeIntent&&horizontal>0&&data.obstructionGraceTicks()>0){
                data.tickGrace();data.increment();data.setState(ProneRecoveryState.RECOVERING);
            }else{
                data.setGrace(0);if(data.state()==ProneRecoveryState.RECOVERING)data.setState(ProneRecoveryState.WAITING_FOR_MOVEMENT);
            }
            data.tickProtection();data.setPreviousPosition(current);
            if(data.accumulatedMovementTicks()>=data.requiredMovementTicks())completeRecovery(player);
            else saved.dirty();
        }
    }
    private static void applyRestrictions(ServerPlayer player,ProneRecoveryData data){
        ProneCondition.apply(player);player.setSprinting(false);if(player.isPassenger())player.stopRiding();
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
        if(speed!=null&&!speed.hasModifier(SPEED_MODIFIER)){
            double reduction=data.reason()==ProneRecoveryReason.FIRST_AWAKENING?-.75:-.65;
            speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER,reduction,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }
    public static void completeRecovery(ServerPlayer player){
        WashedAshoreSavedData saved=WashedAshoreSavedData.get(player.serverLevel());ProneRecoveryData data=saved.player(player.getUUID()).proneRecovery();
        if(!data.active())return;ProneRecoveryReason reason=data.reason();data.complete();clearRestrictions(player);saved.dirty();
        if(reason==ProneRecoveryReason.FIRST_AWAKENING)CampaignMessages.send(player,"inland");
        else CampaignMessages.send(player,"recovered");
        onProneRecoveryCompleted(player,reason);
        CampaignCore.LOGGER.debug("prone_recovery_completed player={} reason={}",player.getUUID(),reason);
    }
    public static void cancelRecovery(ServerPlayer player){
        WashedAshoreSavedData saved=WashedAshoreSavedData.get(player.serverLevel());saved.player(player.getUUID()).proneRecovery().cancel();
        clearRestrictions(player);saved.dirty();CampaignCore.LOGGER.debug("prone_recovery_cancelled player={}",player.getUUID());
    }
    private static void clearRestrictions(ServerPlayer player){
        ProneCondition.remove(player);var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);if(speed!=null)speed.removeModifier(SPEED_MODIFIER);
    }
    public static void onJoin(ServerPlayer player){
        ProneRecoveryData data=WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID()).proneRecovery();
        if(data.active()){data.setPreviousPosition(player.position());applyRestrictions(player,data);}
    }
    public static void onDimensionChange(ServerPlayer player){
        ProneRecoveryData data=WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID()).proneRecovery();
        if(data.active()){data.setPreviousPosition(player.position());applyRestrictions(player,data);}
    }
    public static boolean protectedFromDamage(ServerPlayer player){
        return WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID()).proneRecovery().protectionTicks()>0;
    }
    public static boolean blocksAttack(ServerPlayer player){
        ProneRecoveryData data=WashedAshoreSavedData.get(player.serverLevel()).player(player.getUUID()).proneRecovery();
        if(data.active())data.endProtection();
        return data.active()&&WashedAshoreConfig.INSTANCE.disableAttacksWhileRecovering;
    }
    private static void relocateFromWater(ServerPlayer player){
        ServerLevel level=player.serverLevel();BlockPos origin=player.blockPosition();
        for(int radius=1;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            BlockPos p=origin.offset(dx,0,dz);
            for(int dy=3;dy>=-3;dy--){BlockPos feet=p.offset(0,dy,0);
                if(level.getBlockState(feet.below()).isSolid()&&level.getBlockState(feet).isAir()&&level.getBlockState(feet.above()).isAir()){
                    player.teleportTo(level,feet.getX()+.5,feet.getY(),feet.getZ()+.5,player.getYRot(),player.getXRot());return;
                }}
        }
    }
    private static void onProneRecoveryCompleted(ServerPlayer player,ProneRecoveryReason reason){
        // Stable callback boundary for later resurrection, animation, and quest integrations.
    }
}
