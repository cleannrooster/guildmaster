package dev.campaigncore.client;

import dev.campaigncore.network.ObjectiveMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/** Ordered campaign destinations with one explicit HUD tracking selection. */
public final class ObjectiveMarkerScreen extends Screen {
    private static final List<ObjectiveMarker.Category> ORDER=List.of(
            ObjectiveMarker.Category.OBJECTIVE,ObjectiveMarker.Category.OPPORTUNITY,ObjectiveMarker.Category.LANDMARK);
    private final List<ObjectiveMarker> markers=new ArrayList<>();
    private Set<String> markerIds=Set.of();
    public ObjectiveMarkerScreen(){super(Component.translatable("screen.campaign_core.quest_markers"));}

    @Override protected void init(){
        markers.clear();
        Minecraft mc=Minecraft.getInstance();
        if(mc.level!=null)for(ObjectiveMarker marker:GuideGlowState.targets())
            if(marker.dimension().equals(mc.level.dimension())&&(marker.category()!=ObjectiveMarker.Category.LANDMARK
                    ||CampaignClientConfig.showLandmarks()||isTracked(marker)))markers.add(marker);
        markers.sort(Comparator.comparingInt((ObjectiveMarker marker)->ORDER.indexOf(marker.category()))
                .thenComparing(marker->marker.id().toString()));
        markerIds=currentIds();
        for(ObjectiveMarker.Category category:ORDER){
            List<ObjectiveMarker> group=markers.stream().filter(marker->marker.category()==category).toList();
            int column=ORDER.indexOf(category),center=(column*2+1)*width/6;
            for(int row=0;row<group.size();row++){
                ObjectiveMarker marker=group.get(row);int y=48+row*34;
                addRenderableWidget(Button.builder(trackText(marker),button->{
                    CampaignClientConfig.setTrackedMarker(marker.id().equals(CampaignClientConfig.trackedMarker())?null:marker.id());
                    rebuildWidgets();
                }).bounds(center+42,y,64,20).build());
            }
        }
        addRenderableWidget(Button.builder(landmarkToggleText(),button->{
            CampaignClientConfig.setShowLandmarks(!CampaignClientConfig.showLandmarks());rebuildWidgets();
        }).bounds(8,height-28,150,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->onClose())
                .bounds(width/2-50,height-28,100,20).build());
    }

    @Override public void tick(){if(!currentIds().equals(markerIds))rebuildWidgets();}

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        renderBackground(graphics,mouseX,mouseY,partialTick);super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFF);
        if(markers.isEmpty())graphics.drawCenteredString(font,Component.translatable("screen.campaign_core.quest_markers.none"),width/2,height/2-10,0xA0A0A0);
        Minecraft mc=Minecraft.getInstance();
        for(ObjectiveMarker.Category category:ORDER){
            int column=ORDER.indexOf(category),center=(column*2+1)*width/6;
            graphics.drawCenteredString(font,Component.translatable("marker.campaign_core.category."+category.name().toLowerCase(Locale.ROOT)),center,34,categoryColor(category));
            List<ObjectiveMarker> group=markers.stream().filter(marker->marker.category()==category).toList();
            for(int row=0;row<group.size();row++){
                ObjectiveMarker marker=group.get(row);int x=center-106,y=48+row*34;
                graphics.drawString(font,markerName(marker),x,y+1,isTracked(marker)?0xFFD65A:0xFFFFFF,false);
                graphics.drawString(font,status(marker).copy().append(" • ").append(distance(marker,mc)),x,y+14,0xA0A0A0,false);
            }
        }
    }

    private static Component markerName(ObjectiveMarker marker){return Component.translatable("marker.campaign_core."+marker.id().getPath().replace('/','.'));}
    private static Component status(ObjectiveMarker marker){
        if(isTracked(marker))return Component.translatable("marker.campaign_core.status.tracked");
        return Component.translatable("marker.campaign_core.status."+switch(marker.category()){case OBJECTIVE->"available";case OPPORTUNITY->"temporary";case LANDMARK->"discovered";});
    }
    private static Component distance(ObjectiveMarker marker,Minecraft mc){
        if(mc.player==null)return Component.empty();
        int blocks=(int)Math.round(Math.sqrt(mc.player.blockPosition().distSqr(marker.position()))/10.0)*10;
        return Component.translatable("marker.campaign_core.distance",blocks);
    }
    private static int categoryColor(ObjectiveMarker.Category category){return switch(category){case OBJECTIVE->0xFFFFFF;case OPPORTUNITY->0xFF8055;case LANDMARK->0xA0A0A0;};}
    private static boolean isTracked(ObjectiveMarker marker){return marker.id().equals(CampaignClientConfig.trackedMarker());}
    private static Component trackText(ObjectiveMarker marker){return Component.translatable(isTracked(marker)?"marker.campaign_core.untrack":"marker.campaign_core.track");}
    private static Component landmarkToggleText(){return Component.translatable(CampaignClientConfig.showLandmarks()?"marker.campaign_core.hide_landmarks":"marker.campaign_core.show_landmarks");}
    private static Set<String> currentIds(){Set<String> ids=new HashSet<>();for(ObjectiveMarker marker:GuideGlowState.targets())ids.add(marker.id()+"|"+marker.category());return ids;}
}
