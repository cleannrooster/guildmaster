package dev.campaigncore.washedashore.message;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.message.CampaignMessageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import toni.immersivemessages.api.ImmersiveMessage;
import toni.immersivemessages.api.TextAnchor;

/** Central visual language for Act prompts; all text remains client-localized Components. */
public final class CampaignMessages {
    private CampaignMessages(){}
    public static void send(ServerPlayer player,String id,Object...args){
        CampaignMessageManager.send(player,CampaignCore.washedAshoreId("messages/"+id),args);
    }
    public static void sendIndexed(ServerPlayer player,String id,int index,Object...args){
        CampaignMessageManager.send(player,CampaignCore.washedAshoreId("messages/"+id),index,args);
    }
    public static void story(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(10.0f,tr(key,args)).anchor(TextAnchor.CENTER_CENTER).align(TextAnchor.CENTER_CENTER)
                .y(42).wrap(260).size(1.15f).color(ChatFormatting.WHITE).shadow(true)
                .typewriter(2f,true).fadeIn(.7f).fadeOut(1.0f).sendServer(player);
    }
    public static void objective(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(6.5f,tr(key,args)).anchor(TextAnchor.TOP_CENTER).align(TextAnchor.TOP_CENTER)
                .y(22).wrap(280).size(.92f).color(ChatFormatting.GOLD).bold().background()
                .backgroundColor(0xA0100D08).borderTopColor(0xFFE0A84B).borderBottomColor(0xFF6E4C20)
                .slideDown(.35f).fadeIn(.35f).fadeOut(.8f).sendServer(player);
    }
    public static void quest(ServerPlayer player,String key,int index,Object...args){
        ImmersiveMessage.builder(5.5f,tr(key,args)).anchor(TextAnchor.TOP_CENTER).align(TextAnchor.TOP_CENTER)
                .y(22+index*38).wrap(300).size(.92f).color(ChatFormatting.GOLD).bold().background()
                .backgroundColor(0xA0100D08).borderTopColor(0xFFE0A84B).borderBottomColor(0xFF6E4C20)
                .slideDown(.35f).fadeIn(.35f).fadeOut(.8f).sendServer(player);
    }
    public static void ambient(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(4.2f,tr(key,args)).anchor(TextAnchor.BOTTOM_CENTER).align(TextAnchor.BOTTOM_CENTER)
                .y(-74).wrap(250).size(.82f).color(ChatFormatting.GRAY).italic().shadow(true)
                .fadeIn(.5f).fadeOut(.8f).sendServer(player);
    }
    public static void warning(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(4.0f,tr(key,args)).anchor(TextAnchor.CENTER_CENTER).align(TextAnchor.CENTER_CENTER)
                .y(34).wrap(260).size(1.08f).color(ChatFormatting.RED).bold().shadow(true)
                .shake(8f,.45f).fadeIn(.25f).fadeOut(.8f).sendServer(player);
    }
    public static void completion(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(4.5f,tr(key,args)).anchor(TextAnchor.CENTER_CENTER).align(TextAnchor.CENTER_CENTER)
                .y(48).wrap(260).size(1.05f).color(ChatFormatting.GREEN).bold().background()
                .backgroundColor(0xA008140B).borderTopColor(0xFF69B56E).borderBottomColor(0xFF244D28)
                .slideUp(.35f).fadeIn(.4f).fadeOut(.9f).sendServer(player);
    }
    public static void announcement(ServerPlayer player,String key,Object...args){
        ImmersiveMessage.builder(5.0f,tr(key,args)).anchor(TextAnchor.TOP_CENTER).align(TextAnchor.TOP_CENTER)
                .y(38).wrap(320).size(1.08f).color(ChatFormatting.AQUA).bold().background()
                .backgroundColor(0xA0061018).borderTopColor(0xFF62B6CB).borderBottomColor(0xFF234A57)
                .slideDown(.4f).fadeIn(.4f).fadeOut(1.0f).sendServer(player);
    }
    public static void chat(ServerPlayer player,String key,Object...args){
        player.sendSystemMessage(tr(key,args));
    }
    private static MutableComponent tr(String key,Object...args){return Component.translatable(key,args);}
}
