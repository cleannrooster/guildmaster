package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import dev.campaigncore.washedashore.recovery.ProneRecoveryManager;
import dev.campaigncore.washedashore.message.CampaignMessages;
import dev.campaigncore.washedashore.message.SettlementDialogueNames;
import dev.campaigncore.network.CampaignNetwork;
import dev.campaigncore.network.ObjectiveMarker;
import dev.campaigncore.data.CampaignSavedData;
import dev.campaigncore.washedashore.incident.HubIncidentManager;
import dev.campaigncore.washedashore.incident.HubIncidentState;
import dev.campaigncore.washedashore.worldgen.NaturalStorylineAnchorManager;
import dev.campaigncore.config.CampaignServerConfig;

public final class WashedAshoreManager {
    private static final int LOGIC_INTERVAL=10;
    private static final LocationTriggerRegistry LOCATION_TRIGGERS=new LocationTriggerRegistry();

    /** Named boolean gate a {@link LocationTrigger} may require before firing. */
    @FunctionalInterface private interface TriggerCondition {
        boolean test(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player);
    }
    /** Named glue a {@link LocationTrigger} runs after its generic effects. */
    @FunctionalInterface private interface TriggerHandler {
        void run(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player);
    }
    private static final java.util.Map<String,TriggerCondition> CONDITIONS=java.util.Map.of(
            "other_hub_unplaced",(level,data,player)->!WashedAshoreLayoutGenerator.isOtherHubPlaced(data.act()),
            "other_hub_placed",(level,data,player)->WashedAshoreLayoutGenerator.isOtherHubPlaced(data.act()));
    private static final java.util.Map<String,TriggerHandler> TRIGGER_HANDLERS=java.util.Map.of(
            "settlement_arrival",WashedAshoreManager::onSettlementArrival,
            "reveal_guide",WashedAshoreManager::onRevealGuide,
            "settlement_raid",SettlementRaidManager::onOtherSettlementArrival);

    public static LocationTriggerRegistry locationTriggers(){return LOCATION_TRIGGERS;}
    public static java.util.Set<String> triggerConditions(){return CONDITIONS.keySet();}
    public static java.util.Set<String> triggerHandlers(){return TRIGGER_HANDLERS.keySet();}
    private WashedAshoreManager(){}
    public static void onLevelLoad(ServerLevel level) {
        if(level!=level.getServer().overworld())return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        if(data.act().generationStatus()==WashedAshoreGenerationStatus.UNINITIALIZED&&CampaignServerConfig.generateInitialActLayout()) {
            CampaignCore.LOGGER.info("act_generation_queued_on_world_load dimension={}",level.dimension().location());
            WashedAshoreLayoutGenerator.begin(level,data);
        }
        if(data.act().generationStatus()==WashedAshoreGenerationStatus.SEARCHING){
            if(data.act().worldSpawnStoryline())WashedAshoreLayoutGenerator.resolveLocationsBeforePlayers(level,data);
            else{
                CampaignCore.LOGGER.warn("invalid_natural_primary_search_found_on_load instance={} requested={}; rolling back",
                        data.act().actInstanceId(),data.act().beachSearchCenter());
                data.act().reset();data.act().setWorldSpawnStoryline(false);data.dirty();
            }
        }
    }
    public static void tick(ServerLevel level) {
        if(level!=level.getServer().overworld())return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        // Compatibility fallback for loaders or existing worlds whose load event
        // occurred before this mod initialized.
        if(data.act().generationStatus()==WashedAshoreGenerationStatus.UNINITIALIZED&&CampaignServerConfig.generateInitialActLayout())WashedAshoreLayoutGenerator.begin(level,data);
        WashedAshoreLayoutGenerator.tick(level,data);
        WashedAshoreLayoutGenerator.tickDeferredOtherHub(level,data);
        // Natural anchors must be able to bootstrap the first act. Keeping this behind the
        // contentReady gate made generated landmarks inert whenever automatic initial placement
        // was disabled, because no completed act existed to enable their detector.
        NaturalStorylineAnchorManager.tick(level,data);
        if(!data.act().hasLayout()||data.act().generationStatus()==WashedAshoreGenerationStatus.SEARCHING
                ||data.act().generationStatus()==WashedAshoreGenerationStatus.FAILED)return;
        boolean contentReady=data.act().generationStatus()==WashedAshoreGenerationStatus.COMPLETE
                ||data.act().generationStatus()==WashedAshoreGenerationStatus.DEGRADED;
        if(level.getGameTime()%LOGIC_INTERVAL==0)for(ServerPlayer player:level.players()){
            WashedAshoreProgress progress=data.player(player.getUUID());
            if(!progress.introPlayed()){
                if(playIntro(player,data.act(),progress))data.dirty();
            } else if(contentReady)tickPlayer(level,data,player);
        }
        if(contentReady)EncounterManager.tick(level,data);
        if(contentReady)SettlementRaidManager.tick(level,data);
        if(contentReady)CrossingHordeManager.tick(level,data);
        if(contentReady&&data.act().sculkSurface()==null)WashedAshoreLayoutGenerator.backfillSculkSurface(level,data);
        if(contentReady)SculkSurfaceManager.tick(level,data);
        if(contentReady)HubIncidentManager.tick(level,data);
        // Keep the client snapshot current independently of the show/toggle keybinds. Client-side
        // visibility is preserved when this passive update arrives, so hidden markers stay hidden.
        if(contentReady&&level.getGameTime()%20==0)
            for(ServerPlayer player:level.players())showObjectiveMarkers(player);
    }
    public static void onJoin(ServerPlayer player) {
        ServerLevel level=player.serverLevel();WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        if(data.act().generationStatus()==WashedAshoreGenerationStatus.UNINITIALIZED&&CampaignServerConfig.generateInitialActLayout())WashedAshoreLayoutGenerator.begin(level,data);
        WashedAshoreGenerationStatus status=data.act().generationStatus();
        if(status==WashedAshoreGenerationStatus.UNINITIALIZED&&!CampaignServerConfig.generateInitialActLayout())return;
        if(!data.act().hasLayout()||status==WashedAshoreGenerationStatus.SEARCHING||status==WashedAshoreGenerationStatus.FAILED){
            CampaignMessages.send(player,"world_preparation_failed");
        } else if(status==WashedAshoreGenerationStatus.DEGRADED){
            CampaignMessages.send(player,"world_preparation_degraded");
        }
        if(data.act().hasLayout()&&status!=WashedAshoreGenerationStatus.SEARCHING&&status!=WashedAshoreGenerationStatus.FAILED){
            if(WashedAshoreLayoutGenerator.isHubConstructionPending(data.act())){
                CampaignMessages.send(player,"hub_construction_join");
            }
            WashedAshoreProgress progress=data.player(player.getUUID());
            if(!progress.introPlayed()&&!playIntro(player,data.act(),progress)){
                CampaignMessages.send(player,"world_preparation_failed");
            }
            data.dirty();
        }
    }
    /** Rebuilds the marker set from persistent campaign state, including simultaneous regional quests. */
    public static void showObjectiveMarkers(ServerPlayer player){
        WashedAshoreSavedData data=WashedAshoreSavedData.get(player.serverLevel());
        WashedAshoreInstance act=data.act();
        WashedAshoreProgress progress=data.player(player.getUUID());
        java.util.LinkedHashSet<ObjectiveMarker> targets=new java.util.LinkedHashSet<>();
        WashedAshoreStage stage=progress.stage();
        if(!stage.atLeast(WashedAshoreStage.GUIDE_FOUND)){
            if(act.guideLandmark()!=null&&act.settlement()!=null)
                add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        }else if(!stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED)){
            addEncounterTarget(targets,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        }else if(!stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED)){
            add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        }else if(stage.atLeast(WashedAshoreStage.RAVEN_ROUTE_REVEALED)
                &&!stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED)){
            addEncounterTarget(targets,act,EncounterManager.RAVEN,act.ravenArena(),ObjectiveMarker.Type.RAVEN);
        }else if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)
                &&!stage.atLeast(WashedAshoreStage.RAVEN_ROUTE_REVEALED)){
            if(progress.dreadQuest()!=RegionalQuestStage.COMPLETE)
                addEncounterTarget(targets,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            if(progress.crossingQuest()!=RegionalQuestStage.COMPLETE)
                addEncounterTarget(targets,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            // Regional Encounter C — warn the distant settlement — carries its own marker until repelled.
            if(!act.completedWorldObjectives().contains(EncounterManager.REGIONAL_C))
                addEncounterTarget(targets,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
        }
        // Once revealed, story POIs remain available even after their quest has completed.
        if(stage.atLeast(WashedAshoreStage.GUIDE_FOUND)&&act.guideLandmark()!=null&&act.settlement()!=null)
            add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        if(stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        if(stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        if(stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED))addEncounterTarget(targets,act,EncounterManager.RAVEN,act.ravenArena(),ObjectiveMarker.Type.RAVEN);
        if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)){
            addEncounterTarget(targets,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            addEncounterTarget(targets,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            addEncounterTarget(targets,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
        }
        for(HubIncidentState incident:data.hubIncidentsView().values())if(incident.active()&&incident.center()!=null)
            targets.add(new ObjectiveMarker(incident.activeIncident(),incident.center().above(),ObjectiveMarker.Type.RAID,true));
        for(WashedAshoreInstance instance:data.instances())if(instance!=act)addLayoutMarkers(targets,instance,stage,progress);
        CampaignNetwork.sendObjectiveMarkers(player,nearestMarkers(player,targets));
    }
    private static void addLayoutMarkers(java.util.Set<ObjectiveMarker> targets,WashedAshoreInstance act,WashedAshoreStage stage,WashedAshoreProgress progress){
        if(!stage.atLeast(WashedAshoreStage.GUIDE_FOUND)){
            if(act.guideLandmark()!=null&&act.settlement()!=null)add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        }else if(!stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        else if(!stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        else if(stage.atLeast(WashedAshoreStage.RAVEN_ROUTE_REVEALED)&&!stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED))addEncounterTarget(targets,act,EncounterManager.RAVEN,act.ravenArena(),ObjectiveMarker.Type.RAVEN);
        if(stage.atLeast(WashedAshoreStage.GUIDE_FOUND)&&act.guideLandmark()!=null&&act.settlement()!=null)add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        if(stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        if(stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        if(stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED))addEncounterTarget(targets,act,EncounterManager.RAVEN,act.ravenArena(),ObjectiveMarker.Type.RAVEN);
        if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)){
            addEncounterTarget(targets,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            addEncounterTarget(targets,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            addEncounterTarget(targets,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
        }
    }
    private static java.util.Collection<ObjectiveMarker> nearestMarkers(ServerPlayer player,java.util.Collection<ObjectiveMarker> markers){
        java.util.EnumMap<ObjectiveMarker.Type,ObjectiveMarker> nearest=new java.util.EnumMap<>(ObjectiveMarker.Type.class);
        java.util.List<ObjectiveMarker> result=new java.util.ArrayList<>();
        for(ObjectiveMarker marker:markers){
            if(marker.incident()){result.add(marker);continue;}
            ObjectiveMarker current=nearest.get(marker.type());
            if(current==null||player.blockPosition().distSqr(marker.position())<player.blockPosition().distSqr(current.position()))nearest.put(marker.type(),marker);
        }
        result.addAll(nearest.values());return result;
    }
    private static void add(java.util.Set<ObjectiveMarker> targets,BlockPos position,ObjectiveMarker.Type type){
        if(position!=null)targets.add(new ObjectiveMarker(CampaignCore.washedAshoreId("poi/"+type.name().toLowerCase(java.util.Locale.ROOT)),position.above(),type,false));
    }
    private static void addEncounterTarget(java.util.Set<ObjectiveMarker> targets,WashedAshoreInstance act,
                                           net.minecraft.resources.ResourceLocation id,BlockPos fallback,ObjectiveMarker.Type type){
        var encounter=act.encounters().get(id);
        add(targets,encounter==null?fallback:encounter.bossSpawnPos(),type);
    }
    private static void tickPlayer(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player) {
        WashedAshoreProgress progress=data.player(player.getUUID());WashedAshoreInstance act=data.act();
        for(LocationTrigger trigger:LOCATION_TRIGGERS.all())evaluateTrigger(level,data,player,progress,act,trigger);
        RegionalQuestManager.tick(level,data,player);
        if(progress.stage().atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)
                &&act.completedWorldObjectives().add(WashedAshoreLayoutGenerator.OTHER_HUB_REQUESTED))data.dirty();
    }
    private static void evaluateTrigger(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,
                                        WashedAshoreProgress progress,WashedAshoreInstance act,LocationTrigger trigger){
        BlockPos pos=nearestSlot(data.instances(),player.blockPosition(),trigger.location());
        if(pos==null||!near(player,pos,trigger.radius()))return;
        if(trigger.belowStage().isPresent()&&progress.stage().atLeast(trigger.belowStage().get()))return;
        if(trigger.minStage().isPresent()&&!progress.stage().atLeast(trigger.minStage().get()))return;
        if(trigger.discover().isPresent()&&progress.discoveredLandmarks().contains(trigger.discover().get()))return;
        if(trigger.condition().isPresent()){
            TriggerCondition condition=CONDITIONS.get(trigger.condition().get());
            if(condition==null||!condition.test(level,data,player))return;
        }
        trigger.discover().ifPresent(progress::discover);
        for(WashedAshoreStage stage:trigger.advanceTo())progress.advanceTo(stage);
        trigger.message().ifPresent(id->CampaignMessages.send(player,id));
        trigger.handler().ifPresent(name->{
            TriggerHandler handler=TRIGGER_HANDLERS.get(name);
            if(handler!=null)handler.run(level,data,player);
        });
        data.dirty();
    }
    private static BlockPos nearestSlot(java.util.Collection<WashedAshoreInstance> instances,BlockPos from,String slot){
        BlockPos nearest=null;double distance=Double.MAX_VALUE;
        for(WashedAshoreInstance instance:instances){BlockPos candidate=instance.slot(slot);if(candidate==null)continue;double d=from.distSqr(candidate);if(d<distance){distance=d;nearest=candidate;}}
        return nearest;
    }
    /** Clears the client-side guide glow once a player has reached the guide (stage now GUIDE_FOUND). */
    private static void onRevealGuide(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player){
    }
    /** Complex glue for arriving at the primary settlement: bypasses/records the Undertaker and sets respawn. */
    private static void onSettlementArrival(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player){
        WashedAshoreProgress progress=data.player(player.getUUID());WashedAshoreInstance act=data.act();
        var undertaker=act.encounters().get(EncounterManager.UNDERTAKER);
        if(undertaker!=null&&undertaker.status()!=dev.campaigncore.washedashore.encounter.EncounterStatus.COMPLETED)
            EncounterManager.completeUndertakerBySettlementArrival(level,data,player);
        else if(undertaker!=null){
            progress.defeat(EncounterManager.UNDERTAKER);
            progress.advanceTo(WashedAshoreStage.UNDERTAKER_DEFEATED);
        }
        progress.advanceTo(WashedAshoreStage.SETTLEMENT_REACHED);progress.advanceTo(WashedAshoreStage.REGIONAL_OBJECTIVES);
        // Respawn on safe ground where the player reached the settlement — not settlement().above(),
        // which is one block over the hub centre and lands on top of the main structure.
        if(act.worldSpawnStoryline()){
            BlockPos respawn=SafeSpawnResolver.findNearbySafeFeet(level,player.blockPosition(),16).orElse(player.blockPosition());
            relocateRespawn(level,player,respawn);
        }
        if(progress.defeatedBosses().contains(EncounterManager.UNDERTAKER))CampaignMessages.send(player,"regional");
        else CampaignMessages.send(player,"return_graveyard",SettlementDialogueNames.primary(level,act));
    }
    /**
     * Moves a player's respawn point and tells them, immersively, where it now lies. Uses a forced
     * respawn so an arbitrary ground position is honoured without a bed to validate it; a bed the
     * player later sleeps in still overwrites this normally, so their home stays theirs to change.
     */
    private static void relocateRespawn(ServerLevel level,ServerPlayer player,BlockPos feet){
        player.setRespawnPosition(level.dimension(),feet,0,true,false);
        CampaignMessages.send(player,"respawn_relocated",
                SettlementDialogueNames.at(level,feet,"settlement.campaign_core.washed_ashore.primary"),feet.getX(),feet.getY(),feet.getZ());
        CampaignCore.LOGGER.info("respawn_relocated player={} pos={}",player.getUUID(),feet);
    }
    private static boolean playIntro(ServerPlayer player,WashedAshoreInstance act,WashedAshoreProgress progress) {
        ServerLevel level=player.serverLevel();
        if(!act.worldSpawnStoryline()){
            // Natural and command-created storylines begin in place. They can advance campaign
            // state, but they never commandeer player position or world/player spawn state.
            CampaignMessages.send(player,"washed_ashore");
            progress.markIntroPlayed();progress.advanceTo(WashedAshoreStage.WASHED_ASHORE);
            CampaignSavedData.get(level).mirrorStage(player.getUUID(),WashedAshoreStage.WASHED_ASHORE);
            return true;
        }
        var safe=SafeSpawnResolver.findNearbySafeFeet(level,act.beachSpawn(),16);
        if(safe.isEmpty()){
            CampaignCore.LOGGER.error("beach_spawn_validation_failed stored={} player={}; regenerating layout",act.beachSpawn(),player.getUUID());
            act.reset();
            WashedAshoreLayoutGenerator.begin(level,WashedAshoreSavedData.get(level));
            return false;
        }
        BlockPos p=safe.get();
        BlockPos correctedGround=p.below();
        if(!correctedGround.equals(act.beachSpawn())){
            CampaignCore.LOGGER.warn("beach_spawn_repaired old={} new={} player={}",act.beachSpawn(),correctedGround,player.getUUID());
            act.setBeachSpawn(correctedGround);
        }
        Vec3 target=Vec3.atCenterOf(act.guideLandmark());
        double dx=target.x-(p.getX()+.5),dz=target.z-(p.getZ()+.5);float yaw=(float)(Math.atan2(dz,dx)*180/Math.PI)-90;
        // Pin the world spawn to the validated beach point players wash ashore at. calculateLayout sets
        // it from the raw beach column; re-anchor here so it also reflects any safe-spawn repair above.
        level.setDefaultSpawnPos(p,yaw);
        player.teleportTo(level,p.getX()+.5,p.getY(),p.getZ()+.5,yaw,0);
        CampaignMessages.send(player,"washed_ashore");
        progress.markIntroPlayed();progress.advanceTo(WashedAshoreStage.WASHED_ASHORE);
        CampaignSavedData.get(level).mirrorStage(player.getUUID(),WashedAshoreStage.WASHED_ASHORE);
        ProneRecoveryManager.beginFirstAwakening(player);
        return true;
    }
    private static boolean near(ServerPlayer player,BlockPos pos,int radius){return pos!=null&&player.blockPosition().distSqr(pos)<=radius*radius;}
    public static void triggerRavenSettlementEvent(ServerLevel level,WashedAshoreInstance act){
        for(ServerPlayer player:level.players())CampaignMessages.send(player,"raven_route");
        act.completedWorldObjectives().add(CampaignCore.washedAshoreId("raven_route_revealed"));
    }
    public static void unlockActTwo(ServerLevel level,ServerPlayer player){
        CampaignMessages.send(player,"act_complete");
        CampaignCore.LOGGER.info("act_two_unlock_hook player={}",player.getUUID());
    }
}
