package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BiomeTags;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

public final class WashedAshoreManager {
    private static final int LOGIC_INTERVAL=10;
    private static final int VOID_DARKNESS_TICKS=40;
    private static final java.util.Set<java.util.UUID> VOID_DARKENED_PLAYERS=new java.util.HashSet<>();
    private static final LocationTriggerRegistry LOCATION_TRIGGERS=new LocationTriggerRegistry();

    /** Named boolean gate a {@link LocationTrigger} may require before firing. */
    @FunctionalInterface private interface TriggerCondition {
        boolean test(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreInstance act);
    }
    /** Named glue a {@link LocationTrigger} runs after its generic effects. */
    @FunctionalInterface private interface TriggerHandler {
        void run(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreInstance act);
    }
    private static final java.util.Map<String,TriggerCondition> CONDITIONS=java.util.Map.of(
            "other_hub_unplaced",(level,data,player,act)->!WashedAshoreLayoutGenerator.isOtherHubPlaced(act),
            "other_hub_placed",(level,data,player,act)->WashedAshoreLayoutGenerator.isOtherHubPlaced(act));
    private static final java.util.Map<String,TriggerHandler> TRIGGER_HANDLERS=java.util.Map.of(
            "settlement_arrival",WashedAshoreManager::onSettlementArrival,
            "reveal_guide",WashedAshoreManager::onRevealGuide,
            "settlement_raid",(level,data,player,act)->SettlementRaidManager.onOtherSettlementArrival(level,data,player,act));

    public static LocationTriggerRegistry locationTriggers(){return LOCATION_TRIGGERS;}
    public static java.util.Set<String> triggerConditions(){return CONDITIONS.keySet();}
    public static java.util.Set<String> triggerHandlers(){return TRIGGER_HANDLERS.keySet();}
    private WashedAshoreManager(){}
    public static void onLevelLoad(ServerLevel level) {
        if(level!=level.getServer().overworld())return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(level);
        for(WashedAshoreInstance instance:data.instances())
            if(WashedAshoreLayoutGenerator.repairOverprotectivePlacementFailure(instance)){
                data.dirty();
                CampaignCore.LOGGER.info("regional_placement_failure_reopened instance={}",instance.actInstanceId());
            }
        for(WashedAshoreInstance instance:data.instances())if(instance.hasLayout()){
            int before=instance.encounters().size();
            EncounterManager.registerDefaults(instance);
            if(instance.encounters().size()!=before){
                data.dirty();
                CampaignCore.LOGGER.info("instance_encounters_backfilled instance={} before={} after={}",
                        instance.actInstanceId(),before,instance.encounters().size());
            }
        }
        if(data.act().generationStatus()==WashedAshoreGenerationStatus.FAILED
                &&!data.act().hasLayout()&&CampaignServerConfig.generateInitialActLayout()){
            CampaignCore.LOGGER.info("act_generation_retrying_failed_layout_on_world_load attempts={}",
                    data.act().generationAttempts());
            data.act().reset();data.dirty();
        }
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
        boolean contentReady=data.instances().stream().anyMatch(WashedAshoreManager::isContentReady);
        if(level.getGameTime()%LOGIC_INTERVAL==0)for(ServerPlayer player:level.players()){
            WashedAshoreProgress progress=data.player(player.getUUID());
            if(primaryBeachPending(data,progress)){
                applyVoidWaiting(player,data,progress);continue;
            }
            boolean leftVoid=clearVoidWaiting(player);
            if(data.act().generationStatus()==WashedAshoreGenerationStatus.FAILED){
                if(leftVoid)CampaignMessages.send(player,"world_preparation_failed");
                continue;
            }
            if(!contentReady)continue;
            if(!progress.introPlayed()){
                WashedAshoreInstance instance=isContentReady(data.act())?data.act():nearestReadyInstance(data,player);
                if(instance!=null&&playIntro(player,instance,progress))data.dirty();
            } else if(contentReady)tickPlayer(level,data,player);
        }
        if(!contentReady)return;
        if(contentReady)EncounterManager.tick(level,data);
        if(contentReady)SettlementRaidManager.tick(level,data);
        if(contentReady)CrossingHordeManager.tick(level,data);
        if(contentReady)for(WashedAshoreInstance instance:data.instances())
            if(isContentReady(instance)&&instance.sculkSurface()==null)
                WashedAshoreLayoutGenerator.backfillSculkSurface(level,data,instance);
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
        // A wipe queued by a prestige victory the player logged out on; replaces their progress
        // entry, so it must run before that entry is read below.
        dev.campaigncore.prestige.PrestigeManager.checkPendingWipe(player,data);
        WashedAshoreProgress progress=data.player(player.getUUID());
        if(primaryBeachPending(data,progress)){
            applyVoidWaiting(player,data,progress);return;
        }
        clearVoidWaiting(player);
        if(!progress.introPlayed()&&data.act().generationStatus()==WashedAshoreGenerationStatus.FAILED){
            CampaignMessages.send(player,"world_preparation_failed");return;
        }
        WashedAshoreInstance instance=isContentReady(data.act())?data.act():nearestReadyInstance(data,player);
        WashedAshoreGenerationStatus status=instance==null?data.act().generationStatus():instance.generationStatus();
        if(instance==null&&status==WashedAshoreGenerationStatus.UNINITIALIZED&&!CampaignServerConfig.generateInitialActLayout())return;
        if(instance==null){
            // SEARCHING and PLACING are healthy, transient states. With asynchronous chunk mods a
            // player can join while either is visible; do not misreport that timing window as failure.
            if(status==WashedAshoreGenerationStatus.SEARCHING)
                CampaignMessages.send(player,"world_preparing");
            else if(status==WashedAshoreGenerationStatus.PLACING)
                CampaignMessages.send(player,"hub_construction_join");
            else CampaignMessages.send(player,"world_preparation_failed");
            return;
        } else if(status==WashedAshoreGenerationStatus.DEGRADED){
            CampaignMessages.send(player,"world_preparation_degraded");
        }
        if(instance!=null){
            if(WashedAshoreLayoutGenerator.isHubConstructionPending(instance)){
                CampaignMessages.send(player,"hub_construction_join");
            }
            if(!progress.introPlayed()&&!playIntro(player,instance,progress)){
                CampaignMessages.send(player,"world_preparing");
            }
            data.dirty();
        }
    }
    private static boolean primaryBeachPending(WashedAshoreSavedData data,WashedAshoreProgress progress){
        if(progress.introPlayed()||!CampaignServerConfig.generateInitialActLayout())return false;
        WashedAshoreInstance primary=data.act();
        return primary.worldSpawnStoryline()&&!isContentReady(primary)
                &&(primary.generationStatus()==WashedAshoreGenerationStatus.UNINITIALIZED
                ||primary.generationStatus()==WashedAshoreGenerationStatus.SEARCHING
                ||primary.generationStatus()==WashedAshoreGenerationStatus.PLACING);
    }
    private static void applyVoidWaiting(ServerPlayer player,WashedAshoreSavedData data,WashedAshoreProgress progress){
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS,VOID_DARKNESS_TICKS,0,false,false,false));
        VOID_DARKENED_PLAYERS.add(player.getUUID());
        if(!progress.voidWaitingPlayed()){
            CampaignMessages.send(player,"void_waiting");progress.markVoidWaitingPlayed();data.dirty();
        }
    }
    private static boolean clearVoidWaiting(ServerPlayer player){
        if(!VOID_DARKENED_PLAYERS.remove(player.getUUID()))return false;
        player.removeEffect(MobEffects.DARKNESS);return true;
    }
    /** Rebuilds the marker set from persistent campaign state, including simultaneous regional quests. */
    public static void showObjectiveMarkers(ServerPlayer player){
        ServerLevel overworld=player.server.getLevel(Level.OVERWORLD);
        if(overworld==null)return;
        WashedAshoreSavedData data=WashedAshoreSavedData.get(overworld);
        // The first layout is only a presentation seed; equivalent markers from every other ready
        // layout are collected below and collapsed independently by POI role.
        WashedAshoreInstance act=nearestReadyInstance(data,player);
        if(act==null)return;
        WashedAshoreProgress progress=data.player(player.getUUID());
        java.util.LinkedHashSet<ObjectiveMarker> targets=new java.util.LinkedHashSet<>();
        WashedAshoreStage stage=progress.stage();
        if(!stage.atLeast(WashedAshoreStage.GUIDE_FOUND)){
            if(act.guideLandmark()!=null&&act.settlement()!=null)
                addObjective(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        }else if(!stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED)){
            addObjectiveEncounter(targets,progress,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        }else if(!stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED)){
            addObjective(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        }else if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)){
            if(progress.dreadQuest()!=RegionalQuestStage.COMPLETE)
                addObjectiveEncounter(targets,progress,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            if(progress.crossingQuest()!=RegionalQuestStage.COMPLETE)
                addObjectiveEncounter(targets,progress,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            // Regional Encounter C — warn the distant settlement — carries its own marker until repelled.
            if(progress.crossingQuest()==RegionalQuestStage.COMPLETE
                    &&!progress.defeatedBosses().contains(EncounterManager.REGIONAL_C))
                addObjectiveEncounter(targets,progress,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
            if(EncounterManager.hasCompletedRequiredRegionalObjectives(progress)
                    &&!stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED))
                addObjectiveEncounter(targets,progress,act,EncounterManager.SCULK_SURFACE,act.sculkSurface(),ObjectiveMarker.Type.RAVEN);
        }
        // Once revealed, story POIs remain available even after their quest has completed.
        if(stage.atLeast(WashedAshoreStage.GUIDE_FOUND)&&act.guideLandmark()!=null&&act.settlement()!=null)
            add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        if(stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,progress,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        if(stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)){
            addEncounterTarget(targets,progress,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            addEncounterTarget(targets,progress,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            if(progress.crossingQuest()==RegionalQuestStage.COMPLETE||progress.defeatedBosses().contains(EncounterManager.REGIONAL_C))
                addEncounterTarget(targets,progress,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
        }
        if(stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED)||progress.defeatedBosses().contains(EncounterManager.SCULK_SURFACE))
            addEncounterTarget(targets,progress,act,EncounterManager.SCULK_SURFACE,act.sculkSurface(),ObjectiveMarker.Type.RAVEN);
        if(progress.defeatedBosses().contains(EncounterManager.RAVEN))
            addEncounterTarget(targets,progress,act,EncounterManager.RAVEN,act.ravenArena(),ObjectiveMarker.Type.RAVEN);
        for(HubIncidentState incident:data.hubIncidentsView().values())if(incident.active()&&incident.center()!=null)
            targets.add(new ObjectiveMarker(incident.activeIncident(),Level.OVERWORLD,incident.center().above(),ObjectiveMarker.Type.RAID,ObjectiveMarker.Category.OPPORTUNITY));
        for(WashedAshoreInstance instance:data.instances())
            if(instance!=act&&isContentReady(instance))addLayoutMarkers(targets,instance,stage,progress);
        CampaignNetwork.sendObjectiveMarkers(player,nearestMarkers(player,targets));
    }
    private static boolean isContentReady(WashedAshoreInstance instance){
        return instance.contentReady();
    }
    /** Chooses a nearby physical layout for intro/presentation only; it never owns quest progress. */
    private static WashedAshoreInstance nearestReadyInstance(WashedAshoreSavedData data,ServerPlayer player){
        WashedAshoreInstance nearest=null;double nearestDistance=Double.MAX_VALUE;
        for(WashedAshoreInstance instance:data.instances()){
            if(!isContentReady(instance))continue;
            BlockPos anchor=instance.settlement()!=null?instance.settlement():instance.beachSpawn();
            if(anchor==null)continue;
            double distance=player.blockPosition().distSqr(anchor);
            if(distance<nearestDistance){nearestDistance=distance;nearest=instance;}
        }
        return nearest;
    }
    private static void addLayoutMarkers(java.util.Set<ObjectiveMarker> targets,WashedAshoreInstance act,WashedAshoreStage stage,WashedAshoreProgress progress){
        if(!stage.atLeast(WashedAshoreStage.GUIDE_FOUND)){
            if(act.guideLandmark()!=null&&act.settlement()!=null)add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        }else if(!stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,progress,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        else if(!stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        if(stage.atLeast(WashedAshoreStage.GUIDE_FOUND)&&act.guideLandmark()!=null&&act.settlement()!=null)add(targets,WashedAshoreLayoutGenerator.guideSignPos(act.guideLandmark(),act.settlement()),ObjectiveMarker.Type.GUIDE);
        if(stage.atLeast(WashedAshoreStage.UNDERTAKER_DEFEATED))addEncounterTarget(targets,progress,act,EncounterManager.UNDERTAKER,act.undertakerGraveyard(),ObjectiveMarker.Type.UNDERTAKER);
        if(stage.atLeast(WashedAshoreStage.SETTLEMENT_REACHED))add(targets,act.settlement(),ObjectiveMarker.Type.SETTLEMENT);
        if(stage.atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES)){
            addEncounterTarget(targets,progress,act,EncounterManager.CONSUMING_DREAD,act.darkForest(),ObjectiveMarker.Type.DARK_FOREST);
            addEncounterTarget(targets,progress,act,EncounterManager.THRASHER,act.devilsCrossing(),ObjectiveMarker.Type.DEVILS_CROSSING);
            if(progress.crossingQuest()==RegionalQuestStage.COMPLETE||progress.defeatedBosses().contains(EncounterManager.REGIONAL_C))
                addEncounterTarget(targets,progress,act,EncounterManager.REGIONAL_C,act.otherSettlement(),ObjectiveMarker.Type.SECOND_SETTLEMENT);
        }
        if(stage.atLeast(WashedAshoreStage.RAVEN_DEFEATED)||progress.defeatedBosses().contains(EncounterManager.SCULK_SURFACE))
            addEncounterTarget(targets,progress,act,EncounterManager.SCULK_SURFACE,act.sculkSurface(),ObjectiveMarker.Type.RAVEN);
    }
    private static java.util.Collection<ObjectiveMarker> nearestMarkers(ServerPlayer player,java.util.Collection<ObjectiveMarker> markers){
        java.util.EnumMap<ObjectiveMarker.Type,ObjectiveMarker> nearest=new java.util.EnumMap<>(ObjectiveMarker.Type.class);
        java.util.List<ObjectiveMarker> result=new java.util.ArrayList<>();
        for(ObjectiveMarker marker:markers){
            if(marker.category()==ObjectiveMarker.Category.OPPORTUNITY){result.add(marker);continue;}
            ObjectiveMarker current=nearest.get(marker.type());
            if(current==null||marker.category().ordinal()<current.category().ordinal()
                    ||(marker.category()==current.category()&&player.blockPosition().distSqr(marker.position())<player.blockPosition().distSqr(current.position())))nearest.put(marker.type(),marker);
        }
        result.addAll(nearest.values());return result;
    }
    private static void add(java.util.Set<ObjectiveMarker> targets,BlockPos position,ObjectiveMarker.Type type){
        add(targets,position,type,ObjectiveMarker.Category.LANDMARK);
    }
    private static void addObjective(java.util.Set<ObjectiveMarker> targets,BlockPos position,ObjectiveMarker.Type type){
        add(targets,position,type,ObjectiveMarker.Category.OBJECTIVE);
    }
    private static void add(java.util.Set<ObjectiveMarker> targets,BlockPos position,ObjectiveMarker.Type type,ObjectiveMarker.Category category){
        if(position!=null)targets.add(new ObjectiveMarker(CampaignCore.washedAshoreId("poi/"+type.name().toLowerCase(java.util.Locale.ROOT)),Level.OVERWORLD,position.above(),type,category));
    }
    private static void addEncounterTarget(java.util.Set<ObjectiveMarker> targets,WashedAshoreProgress progress,WashedAshoreInstance act,
                                           net.minecraft.resources.ResourceLocation id,BlockPos fallback,ObjectiveMarker.Type type){
        var encounter=act.encounters().get(id);
        // A completed physical copy is replayable for a different player who still needs the shared
        // objective, so it remains a valid marker destination. Keep the marker on the authored POI:
        // event-driven encounters may temporarily move their combat anchor near the triggering player.
        add(targets,fallback!=null?fallback:encounter==null?null:encounter.anchorPos(),type);
    }
    private static void addObjectiveEncounter(java.util.Set<ObjectiveMarker> targets,WashedAshoreProgress progress,WashedAshoreInstance act,
                                               net.minecraft.resources.ResourceLocation id,BlockPos fallback,ObjectiveMarker.Type type){
        var encounter=act.encounters().get(id);
        add(targets,fallback!=null?fallback:encounter==null?null:encounter.anchorPos(),type,ObjectiveMarker.Category.OBJECTIVE);
    }
    private static void tickPlayer(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player) {
        WashedAshoreProgress progress=data.player(player.getUUID());
        for(LocationTrigger trigger:LOCATION_TRIGGERS.all())evaluateTrigger(level,data,player,progress,trigger);
        RegionalQuestManager.tick(level,data,player);
        if(progress.stage().atLeast(WashedAshoreStage.REGIONAL_OBJECTIVES))for(WashedAshoreInstance act:data.instances())
            if(isContentReady(act)&&act.completedWorldObjectives().add(WashedAshoreLayoutGenerator.OTHER_HUB_REQUESTED)){
                data.dirty();
                CampaignCore.LOGGER.info("regional_construction_requested player={} instance={} crossing={} other_settlement={}",
                        player.getUUID(),act.actInstanceId(),act.devilsCrossing(),act.otherSettlement());
            }
    }
    private static void evaluateTrigger(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,
                                        WashedAshoreProgress progress,LocationTrigger trigger){
        WashedAshoreInstance triggerAct=nearestReadyInstance(data.instances(),player.blockPosition(),trigger.location());
        BlockPos pos=triggerAct==null?null:triggerAct.slot(trigger.location());
        if(pos==null||!near(player,pos,trigger.radius()))return;
        if(trigger.belowStage().isPresent()&&progress.stage().atLeast(trigger.belowStage().get()))return;
        if(trigger.minStage().isPresent()&&!progress.stage().atLeast(trigger.minStage().get()))return;
        if(trigger.discover().isPresent()&&progress.discoveredLandmarks().contains(trigger.discover().get()))return;
        if(trigger.condition().isPresent()){
            TriggerCondition condition=CONDITIONS.get(trigger.condition().get());
            if(condition==null||!condition.test(level,data,player,triggerAct))return;
        }
        trigger.discover().ifPresent(progress::discover);
        for(WashedAshoreStage stage:trigger.advanceTo())progress.advanceTo(stage);
        trigger.message().ifPresent(id->CampaignMessages.send(player,id));
        trigger.handler().ifPresent(name->{
            TriggerHandler handler=TRIGGER_HANDLERS.get(name);
            if(handler!=null)handler.run(level,data,player,triggerAct);
        });
        data.dirty();
    }
    private static WashedAshoreInstance nearestReadyInstance(java.util.Collection<WashedAshoreInstance> instances,BlockPos from,String slot){
        WashedAshoreInstance nearest=null;double distance=Double.MAX_VALUE;
        for(WashedAshoreInstance instance:instances){
            if(!isContentReady(instance))continue;
            BlockPos candidate=instance.slot(slot);if(candidate==null)continue;
            double d=from.distSqr(candidate);if(d<distance){distance=d;nearest=instance;}
        }
        return nearest;
    }
    /** Clears the client-side guide glow once a player has reached the guide (stage now GUIDE_FOUND). */
    private static void onRevealGuide(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreInstance act){
    }
    /** Complex glue for arriving at the primary settlement: bypasses/records the Undertaker and sets respawn. */
    private static void onSettlementArrival(ServerLevel level,WashedAshoreSavedData data,ServerPlayer player,WashedAshoreInstance act){
        WashedAshoreProgress progress=data.player(player.getUUID());
        var undertaker=act.encounters().get(EncounterManager.UNDERTAKER);
        if(undertaker!=null&&undertaker.status()!=dev.campaigncore.washedashore.encounter.EncounterStatus.COMPLETED)
            EncounterManager.completeUndertakerBySettlementArrival(level,data,player,act);
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
        if(!act.completedWorldObjectives().add(CampaignCore.washedAshoreId("raven_route_revealed")))return;
        for(ServerPlayer player:level.players())CampaignMessages.send(player,"raven_route");
    }
    public static void unlockActTwo(ServerLevel level,ServerPlayer player){
        CampaignMessages.send(player,"act_complete");
        CampaignCore.LOGGER.info("act_two_unlock_hook player={}",player.getUUID());
    }
}
