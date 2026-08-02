package dev.campaigncore.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientGuiEvent;
import dev.architectury.networking.NetworkManager;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import dev.campaigncore.network.CampaignNetwork;
import dev.campaigncore.network.ObjectiveMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class CampaignCoreClient {
    private static final KeyMapping SHOW_AVAILABLE_QUESTS=new KeyMapping(
            "key.campaign_core.show_available_quests",InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_GRAVE_ACCENT,"key.categories.campaign_core");
    private static final KeyMapping TOGGLE_QUEST_MARKERS=new KeyMapping(
            "key.campaign_core.toggle_quest_markers",InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,"key.categories.campaign_core");
    private static boolean connected;
    private static int joinPlaybackDelay=-1;
    private static boolean initialized;
    private CampaignCoreClient(){}
    public static void init(){
        if(initialized)return;
        initialized=true;
        CampaignClientConfig.load();
        KeyMappingRegistry.register(SHOW_AVAILABLE_QUESTS);
        KeyMappingRegistry.register(TOGGLE_QUEST_MARKERS);
        registerObjectiveMarkerReceiver();
        ClientGuiEvent.RENDER_HUD.register(ObjectiveMarkerHudRenderer::render);
        ClientTickEvent.CLIENT_POST.register(client->{
            boolean hasConnection=client.getConnection()!=null;
            if(hasConnection)GuideGlowState.tick();
            if(hasConnection&&!connected){connected=true;joinPlaybackDelay=40;}
            else if(!hasConnection){connected=false;joinPlaybackDelay=-1;GuideGlowState.reset();}
            if(joinPlaybackDelay>0&&--joinPlaybackDelay==0&&CampaignClientConfig.showAvailableQuestsOnServerJoin())
                CampaignClientNetwork.requestAvailableQuests(false);
            while(hasConnection&&SHOW_AVAILABLE_QUESTS.consumeClick()){
                GuideGlowState.revealOnNextUpdate();
                CampaignClientNetwork.requestAvailableQuests(true);
            }
            while(hasConnection&&TOGGLE_QUEST_MARKERS.consumeClick()){
                CampaignClientNetwork.requestObjectiveMarkers();
                client.setScreen(new ObjectiveMarkerScreen());
            }
        });
    }
    @SuppressWarnings("removal")
    private static void registerObjectiveMarkerReceiver(){
        NetworkManager.registerReceiver(NetworkManager.Side.S2C,CampaignNetwork.OBJECTIVE_MARKERS,(buffer,context)->{
            int count=buffer.readVarInt();
            java.util.List<ObjectiveMarker> targets=new java.util.ArrayList<>(count);
            for(int i=0;i<count;i++)targets.add(new ObjectiveMarker(buffer.readResourceLocation(),buffer.readBlockPos(),
                    buffer.readEnum(ObjectiveMarker.Type.class),buffer.readBoolean()));
            context.queue(()->GuideGlowState.update(targets));
        });
    }
}
