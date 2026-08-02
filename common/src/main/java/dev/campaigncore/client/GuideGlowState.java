package dev.campaigncore.client;

import dev.campaigncore.network.ObjectiveMarker;
import java.util.List;

/** Client-only snapshot of every currently active campaign objective marker. */
public final class GuideGlowState {
    private static final int VISIBLE_TICKS=10*20;
    private static volatile List<ObjectiveMarker> targets=List.of();
    private static int ticksRemaining;
    private static boolean revealOnNextUpdate;
    private GuideGlowState(){}
    /** Replaces marker data without extending an existing transient display. */
    public static void update(List<ObjectiveMarker> positions){
        targets=List.copyOf(positions);
        if(revealOnNextUpdate)ticksRemaining=VISIBLE_TICKS;
        revealOnNextUpdate=false;
    }
    public static void revealOnNextUpdate(){revealOnNextUpdate=true;}
    public static void tick(){if(ticksRemaining>0)--ticksRemaining;}
    public static void clear(){targets=List.of();ticksRemaining=0;}
    public static void reset(){revealOnNextUpdate=false;clear();}
    public static List<ObjectiveMarker> targets(){return targets;}
    public static boolean visible(ObjectiveMarker marker){return ticksRemaining>0||CampaignClientConfig.markerVisible(marker.id());}
}
