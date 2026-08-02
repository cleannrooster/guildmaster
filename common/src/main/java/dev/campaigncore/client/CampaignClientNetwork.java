package dev.campaigncore.client;

import dev.architectury.networking.NetworkManager;
import dev.campaigncore.network.CampaignNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;

public final class CampaignClientNetwork {
    private CampaignClientNetwork(){}
    @SuppressWarnings("removal")
    public static void requestAvailableQuests(boolean keybindPressed){
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null){
            RegistryFriendlyByteBuf buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(1),connection.registryAccess());
            buffer.writeBoolean(keybindPressed);
            NetworkManager.sendToServer(CampaignNetwork.SHOW_AVAILABLE_QUESTS,buffer);
        }
    }
    @SuppressWarnings("removal")
    public static void requestObjectiveMarkers(){
        var connection=Minecraft.getInstance().getConnection();
        if(connection!=null)NetworkManager.sendToServer(CampaignNetwork.REQUEST_OBJECTIVE_MARKERS,
                new RegistryFriendlyByteBuf(Unpooled.buffer(0),connection.registryAccess()));
    }
}
