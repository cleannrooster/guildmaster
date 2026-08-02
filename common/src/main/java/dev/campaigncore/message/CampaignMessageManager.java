package dev.campaigncore.message;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.message.CampaignMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class CampaignMessageManager {
    private static final CampaignMessageRegistry REGISTRY=new CampaignMessageRegistry();
    private CampaignMessageManager(){}
    public static CampaignMessageRegistry registry(){return REGISTRY;}
    public static void send(ServerPlayer player,ResourceLocation id,Object...args){send(player,id,0,args);}
    public static void send(ServerPlayer player,ResourceLocation id,int index,Object...args){
        CampaignMessageDefinition definition=REGISTRY.get(id).orElse(null);
        if(definition==null){
            CampaignCore.LOGGER.warn("campaign_message_missing id={} player={}",id,player.getUUID());
            player.sendSystemMessage(Component.literal("["+id+"]"));
            return;
        }
        try{
            switch(definition.channel()){
                case STORY -> CampaignMessages.story(player,definition.translation(),args);
                case OBJECTIVE -> CampaignMessages.objective(player,definition.translation(),args);
                case QUEST -> CampaignMessages.quest(player,definition.translation(),index,args);
                case AMBIENT -> CampaignMessages.ambient(player,definition.translation(),args);
                case WARNING -> CampaignMessages.warning(player,definition.translation(),args);
                case COMPLETION -> CampaignMessages.completion(player,definition.translation(),args);
                case ANNOUNCEMENT -> CampaignMessages.announcement(player,definition.translation(),args);
                case CHAT -> CampaignMessages.chat(player,definition.translation(),args);
            }
        }catch(RuntimeException|LinkageError ex){
            CampaignCore.LOGGER.warn("immersive_message_failed id={} player={}; using chat fallback",id,player.getUUID(),ex);
            player.sendSystemMessage(Component.translatable(definition.translation(),args));
        }
    }
}
