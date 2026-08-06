package dev.campaigncore.network;

import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.message.CampaignQuestMessages;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignNetwork {
    public static final net.minecraft.resources.ResourceLocation SHOW_AVAILABLE_QUESTS=CampaignCore.id("show_available_quests");
    public static final net.minecraft.resources.ResourceLocation OBJECTIVE_MARKERS=CampaignCore.id("objective_markers");
    public static final net.minecraft.resources.ResourceLocation REQUEST_OBJECTIVE_MARKERS=CampaignCore.id("request_objective_markers");
    private CampaignNetwork(){}
    @SuppressWarnings("removal")
    public static void registerServer(){
        // On a physical client, registerObjectiveMarkerReceiver() registers both the S2C type and
        // its receiver. Registering the server-only type here as well is a duplicate on NeoForge.
        if(Platform.getEnvironment()==Env.SERVER)NetworkManager.registerS2CPayloadType(OBJECTIVE_MARKERS);
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,SHOW_AVAILABLE_QUESTS,(buffer,context)->{
            boolean keybindPressed=buffer.readBoolean();
            context.queue(()->{
                    if(context.getPlayer() instanceof ServerPlayer player){
                        CampaignQuestMessages.playAvailable(player,keybindPressed);
                        if(keybindPressed)dev.campaigncore.washedashore.act.WashedAshoreManager.showObjectiveMarkers(player);
                    }
            });
        });
        NetworkManager.registerReceiver(NetworkManager.Side.C2S,REQUEST_OBJECTIVE_MARKERS,(buffer,context)->
                context.queue(()->{
                    if(context.getPlayer() instanceof ServerPlayer player)
                        dev.campaigncore.washedashore.act.WashedAshoreManager.showObjectiveMarkers(player);
                }));
    }
    /** S2C: replaces the client's complete set of active objective marker positions. */
    @SuppressWarnings("removal")
    public static void sendObjectiveMarkers(ServerPlayer player,java.util.Collection<ObjectiveMarker> targets){
        RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),player.registryAccess());
        buffer.writeVarInt(targets.size());
        for(ObjectiveMarker target:targets){
            buffer.writeResourceLocation(target.id());
            buffer.writeResourceLocation(target.dimension().location());
            buffer.writeBlockPos(target.position());
            buffer.writeEnum(target.type());
            buffer.writeEnum(target.category());
        }
        NetworkManager.sendToPlayer(player,OBJECTIVE_MARKERS,buffer);
    }
}
