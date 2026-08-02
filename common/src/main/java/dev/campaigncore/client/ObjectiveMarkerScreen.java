package dev.campaigncore.client;

import dev.campaigncore.network.ObjectiveMarker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Simple per-POI pinning menu opened by the quest-marker key. */
public final class ObjectiveMarkerScreen extends Screen {
    private final List<ObjectiveMarker> markers=new ArrayList<>();
    private Set<String> markerIds=Set.of();
    public ObjectiveMarkerScreen(){super(Component.translatable("screen.campaign_core.quest_markers"));}

    @Override protected void init(){
        markers.clear();markers.addAll(GuideGlowState.targets());
        markers.sort(Comparator.comparing(marker->marker.id().toString()));
        markerIds=currentIds();
        int rows=Math.min(7,markers.size()),startY=Math.max(38,height/2-rows*12);
        for(int i=0;i<markers.size();i++){
            ObjectiveMarker marker=markers.get(i);int column=i/7,row=i%7,columnCenter=markers.size()>7?(column==0?width/4:width*3/4):width/2,y=startY+row*24;
            addRenderableWidget(Button.builder(toggleText(marker),button->{
                CampaignClientConfig.setMarkerVisible(marker.id(),!CampaignClientConfig.markerVisible(marker.id()));
                button.setMessage(toggleText(marker));
            }).bounds(columnCenter+38,y,62,20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button->onClose())
                .bounds(width/2-50,height-28,100,20).build());
    }

    @Override public void tick(){
        Set<String> current=currentIds();
        if(!current.equals(markerIds))rebuildWidgets();
    }

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        renderBackground(graphics,mouseX,mouseY,partialTick);super.render(graphics,mouseX,mouseY,partialTick);
        graphics.drawCenteredString(font,title,width/2,16,0xFFFFFF);
        if(markers.isEmpty())graphics.drawCenteredString(font,Component.translatable("screen.campaign_core.quest_markers.none"),width/2,height/2-10,0xA0A0A0);
        int rows=Math.min(7,markers.size()),startY=Math.max(38,height/2-rows*12);
        for(int i=0;i<markers.size();i++){
            ObjectiveMarker marker=markers.get(i);int column=i/7,row=i%7,columnCenter=markers.size()>7?(column==0?width/4:width*3/4):width/2;
            Component name=Component.translatable("marker.campaign_core."+marker.id().getPath().replace('/','.'));
            graphics.drawString(font,name,columnCenter-100,startY+row*24+6,marker.incident()?0xFFAA55:0xFFFFFF,false);
        }
    }
    private static Component toggleText(ObjectiveMarker marker){return Component.translatable(CampaignClientConfig.markerVisible(marker.id())?"options.on":"options.off");}
    private static Set<String> currentIds(){Set<String> ids=new HashSet<>();for(ObjectiveMarker marker:GuideGlowState.targets())ids.add(marker.id().toString());return ids;}
}
