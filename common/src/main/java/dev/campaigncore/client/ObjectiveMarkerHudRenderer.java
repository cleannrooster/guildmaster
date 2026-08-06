package dev.campaigncore.client;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.network.ObjectiveMarker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** HUD-layer objective icons, projected by camera direction and clamped to the visible screen. */
public final class ObjectiveMarkerHudRenderer {
    private static final int ICON_SIZE=32;
    private static final int EDGE_MARGIN=6;
    private static final double INCIDENT_WARNING_RANGE_SQR=192.0*192.0;
    private ObjectiveMarkerHudRenderer(){}

    public static void render(GuiGraphics graphics,DeltaTracker deltaTracker){
        Minecraft mc=Minecraft.getInstance();
        if(mc.cameraEntity==null||mc.level==null||GuideGlowState.targets().isEmpty()||mc.options.hideGui)return;
        Camera camera=mc.gameRenderer.getMainCamera();
        Vec3 cameraPos=camera.getPosition();
        Vector3f look=camera.getLookVector(),left=camera.getLeftVector(),up=camera.getUpVector();
        int width=graphics.guiWidth(),height=graphics.guiHeight();
        double verticalFov=Math.toRadians(mc.options.fov().get());
        double horizontalFov=2*Math.atan(Math.tan(verticalFov/2.0)*width/(double)height);
        double focalPixels=(height/2.0)/Math.tan(verticalFov/2.0);
        for(ObjectiveMarker marker:GuideGlowState.targets()){
            if(!marker.dimension().equals(mc.level.dimension()))continue;
            Vec3 direction=Vec3.atCenterOf(marker.position()).subtract(cameraPos);
            boolean tracked=GuideGlowState.visible(marker);
            boolean incidentWarning=marker.category()==ObjectiveMarker.Category.OPPORTUNITY
                    &&(GuideGlowState.incidentNew(marker)||direction.lengthSqr()<=INCIDENT_WARNING_RANGE_SQR);
            if(!tracked&&!incidentWarning)continue;
            double forward=dot(direction,look);
            double towardLeft=dot(direction,left);
            double towardUp=dot(direction,up);
            double rawX,rawY;
            if(forward>1.0e-3){
                // Minecraft's perspective projection is proportional to tan(angle), not angle.
                // Using the component ratios also keeps the icon locked to the camera basis.
                rawX=width/2.0-towardLeft/forward*focalPixels;
                rawY=height/2.0-towardUp/forward*focalPixels;
            }else{
                // A point behind the camera cannot be perspectively projected. Preserve the old
                // angular mapping solely to choose the appropriate screen edge.
                double horizontalAngle=Math.atan2(-towardLeft,forward);
                double verticalAngle=Math.atan2(towardUp,Math.sqrt(forward*forward+towardLeft*towardLeft));
                rawX=width/2.0+horizontalAngle/(horizontalFov/2.0)*(width/2.0);
                rawY=height/2.0-verticalAngle/(verticalFov/2.0)*(height/2.0);
            }
            int x=clamp((int)Math.round(rawX)-ICON_SIZE/2,EDGE_MARGIN,width-ICON_SIZE-EDGE_MARGIN);
            int y=clamp((int)Math.round(rawY)-ICON_SIZE/2,EDGE_MARGIN,height-ICON_SIZE-EDGE_MARGIN);
            int border=tracked?0xFFFFC43D:0xFFFF5A36;
            graphics.fill(x-2,y-2,x+ICON_SIZE+2,y+ICON_SIZE+2,border);
            graphics.fill(x,y,x+ICON_SIZE,y+ICON_SIZE,0xCC17120B);
            graphics.blit(texture(marker.type()),x,y,0,0,ICON_SIZE,ICON_SIZE,32,32);
            if(tracked){
                var label=net.minecraft.network.chat.Component.translatable("marker.campaign_core."+marker.id().getPath().replace('/','.'));
                int labelWidth=mc.font.width(label),labelX=clamp(x+ICON_SIZE/2-labelWidth/2,EDGE_MARGIN,width-labelWidth-EDGE_MARGIN);
                int labelY=y+ICON_SIZE+15<height?y+ICON_SIZE+5:y-12;
                graphics.fill(labelX-3,labelY-2,labelX+labelWidth+3,labelY+10,0xCC17120B);
                graphics.drawString(mc.font,label,labelX,labelY,0xFFFFC43D,false);
            }
        }
    }

    private static double dot(Vec3 vector,Vector3f axis){
        return vector.x*axis.x()+vector.y*axis.y()+vector.z*axis.z();
    }
    private static int clamp(int value,int minimum,int maximum){return Math.max(minimum,Math.min(maximum,value));}
    private static ResourceLocation texture(ObjectiveMarker.Type type){

        String name=switch(type){
            case GUIDE -> "sign";
            case UNDERTAKER -> "undertaker";
            case SETTLEMENT -> "settlement";
            case RAVEN -> "raven";
            case DARK_FOREST -> "forest";
            case DEVILS_CROSSING -> "crossing";
            case SECOND_SETTLEMENT -> "settlement_2";
            case RAID -> "raid";
        };

        return CampaignCore.id("textures/objective_markers/"+name+"_ico.png");
    }
}
