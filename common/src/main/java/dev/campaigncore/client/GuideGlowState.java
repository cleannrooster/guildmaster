package dev.campaigncore.client;

import dev.campaigncore.network.ObjectiveMarker;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Client-only snapshot of every currently active campaign objective marker. */
public final class GuideGlowState {
    private static final int VISIBLE_TICKS=10*20;
    private static volatile List<ObjectiveMarker> targets=List.of();
    private static int ticksRemaining;
    private static boolean revealOnNextUpdate;
    private static final int INCIDENT_WARNING_TICKS=10*20;
    private static final Map<net.minecraft.resources.ResourceLocation,Integer> incidentWarnings=new HashMap<>();
    private GuideGlowState(){}
    /** Replaces marker data without extending an existing transient display. */
    public static void update(List<ObjectiveMarker> positions){
        var tracked=CampaignClientConfig.trackedMarker();
        ObjectiveMarker previousTracked=tracked==null?null:targets.stream().filter(marker->marker.id().equals(tracked)).findFirst().orElse(null);
        for(ObjectiveMarker marker:positions)if(marker.category()==ObjectiveMarker.Category.OPPORTUNITY
                &&targets.stream().noneMatch(old->old.id().equals(marker.id())))incidentWarnings.put(marker.id(),INCIDENT_WARNING_TICKS);
        targets=List.copyOf(positions);
        ObjectiveMarker currentTracked=tracked==null?null:targets.stream().filter(marker->marker.id().equals(tracked)).findFirst().orElse(null);
        boolean completedTrackedObjective=previousTracked!=null&&previousTracked.category()==ObjectiveMarker.Category.OBJECTIVE
                &&currentTracked!=null&&currentTracked.category()==ObjectiveMarker.Category.LANDMARK;
        if(currentTracked==null||completedTrackedObjective){
            ObjectiveMarker replacement=targets.stream().filter(marker->marker.category()==ObjectiveMarker.Category.OBJECTIVE).findFirst()
                    .orElseGet(()->targets.stream().filter(marker->marker.category()==ObjectiveMarker.Category.OPPORTUNITY).findFirst().orElse(null));
            CampaignClientConfig.setTrackedMarker(replacement==null?null:replacement.id());
        }
        if(revealOnNextUpdate)ticksRemaining=VISIBLE_TICKS;
        revealOnNextUpdate=false;
    }
    public static void revealOnNextUpdate(){revealOnNextUpdate=true;}
    public static void tick(){
        if(ticksRemaining>0)--ticksRemaining;
        incidentWarnings.replaceAll((id,ticks)->ticks-1);incidentWarnings.values().removeIf(ticks->ticks<=0);
    }
    public static void clear(){targets=List.of();ticksRemaining=0;incidentWarnings.clear();}
    public static void reset(){revealOnNextUpdate=false;clear();}
    public static List<ObjectiveMarker> targets(){return targets;}
    public static boolean visible(ObjectiveMarker marker){return marker.id().equals(CampaignClientConfig.trackedMarker());}
    public static boolean incidentNew(ObjectiveMarker marker){return incidentWarnings.containsKey(marker.id());}
}
