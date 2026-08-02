package dev.campaigncore.washedashore.recovery;

import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

/** Authoritative prone state plus the synchronized client visual signal. */
public final class ProneCondition {
    private ProneCondition(){}
    public static void apply(ServerPlayer player){player.setSwimming(true);player.setPose(Pose.SWIMMING);player.setSprinting(false);player.stopRiding();player.refreshDimensions();}
    public static void remove(ServerPlayer player){player.setSwimming(false);if(player.getPose()==Pose.SWIMMING)player.setPose(Pose.STANDING);player.refreshDimensions();}
    public static boolean isApplied(ServerPlayer player){return player.getPose()==Pose.SWIMMING;}
    public static boolean shouldForcePose(Player player){
        if(player instanceof ServerPlayer serverPlayer)
            return WashedAshoreSavedData.get(serverPlayer.serverLevel()).player(serverPlayer.getUUID()).proneRecovery().active();
        return player.isSwimming()&&!player.isInWater();
    }
}
