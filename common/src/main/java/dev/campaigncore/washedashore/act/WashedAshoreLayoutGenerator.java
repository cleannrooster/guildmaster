package dev.campaigncore.washedashore.act;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.settlers.expansion.FrontierHubRuntimePlacer;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.campaigncore.washedashore.config.WashedAshoreConfig;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** A bounded, one-candidate-per-tick layout search followed by small synchronous placements. */
public final class WashedAshoreLayoutGenerator {
    private static final ResourceLocation PRIMARY_HUB_PLACED=CampaignCore.washedAshoreId("primary_frontier_hub_placed");
    private static final ResourceLocation OTHER_HUB_PLACED=CampaignCore.washedAshoreId("other_frontier_hub_placed");
    public static final ResourceLocation OTHER_HUB_REQUESTED=CampaignCore.washedAshoreId("other_frontier_hub_requested");
    private static final ResourceLocation OTHER_HUB_FAILED=CampaignCore.washedAshoreId("other_frontier_hub_failed");
    private static final ResourceLocation DEVILS_CROSSING_RUIN_PLACED=CampaignCore.washedAshoreId("devils_crossing_ruin_placed");
    private static final ResourceLocation DEVILS_CROSSING_RUIN_FAILED=CampaignCore.washedAshoreId("devils_crossing_ruin_failed");
    private static final int HUB_CHUNK_RADIUS=10;
    private static final int HUB_CHUNKS_PER_TICK=8;
    private static final int HUB_TICKET_LEVEL=2;
    private static final TicketType<ChunkPos> HUB_GENERATION_TICKET=
            TicketType.create("campaign_core_washed_ashore_frontier_hub",Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<String,HubLoadState> HUB_LOADS=new HashMap<>();
    private static final int MAX_HUB_RELOCATIONS=6;
    private static final int HUB_RELOCATION_FOOTPRINT=16;
    private static final int RUIN_RELOCATION_FOOTPRINT=8;
    /** Sculk Surface sits beyond the Dark Forest, on the settlement→forest bearing, this much further. */
    private static final int SCULK_BEYOND_FOREST=320;
    private static final int SCULK_BEYOND_JITTER=200;
    private static final int SCULK_SEARCH_RADIUS=224;
    private static final int SCULK_FOOTPRINT=12;
    /** Fallback: any eligible dry site within a relatively large distance of the settlement. */
    private static final int SCULK_FALLBACK_DISTANCE=520;
    private static final int SCULK_FALLBACK_SEARCH_RADIUS=768;
    /** Outcome of polling/starting a Settlers frontier-hub job for one tick. */
    private enum HubJobResult { WAITING, ENDED, REJECTED }
    private WashedAshoreLayoutGenerator(){}
    public static boolean isOtherHubPlaced(WashedAshoreInstance act){
        return act.completedWorldObjectives().contains(OTHER_HUB_PLACED);
    }
    public static boolean isDevilsCrossingPlaced(WashedAshoreInstance act){
        return act.completedWorldObjectives().contains(DEVILS_CROSSING_RUIN_PLACED);
    }
    public static boolean isHubConstructionPending(WashedAshoreInstance act){
        return act.generationStatus()==WashedAshoreGenerationStatus.PLACING
                ||(act.completedWorldObjectives().contains(OTHER_HUB_REQUESTED)
                &&!act.completedWorldObjectives().contains(OTHER_HUB_PLACED)
                &&!act.completedWorldObjectives().contains(OTHER_HUB_FAILED));
    }
    public static void begin(ServerLevel level,WashedAshoreSavedData data) {
        WashedAshoreInstance act=data.act();
        if(act.generationStatus()!=WashedAshoreGenerationStatus.UNINITIALIZED&&act.generationStatus()!=WashedAshoreGenerationStatus.FAILED)return;
        act.setWorldSpawnStoryline(true);
        act.setStatus(WashedAshoreGenerationStatus.SEARCHING);data.dirty();
        CampaignCore.LOGGER.info("act_generation_started spawn={} radius={}",level.getSharedSpawnPos(),WashedAshoreConfig.INSTANCE.beachSearchRadius);
    }
    /**
     * Resolves every story coordinate during level load. This intentionally uses
     * a finite synchronous budget so no player can observe a half-resolved Act
     * layout; expensive structure/chunk placement remains staged in {@link #tick}.
     */
    public static boolean resolveLocationsBeforePlayers(ServerLevel level,WashedAshoreSavedData data) {
        WashedAshoreInstance act=data.act();
        try {
            int budget=WashedAshoreConfig.INSTANCE.generationAttemptLimit+2;
            while(act.generationStatus()==WashedAshoreGenerationStatus.SEARCHING&&budget-->0)searchOne(level,data);
            if(act.generationStatus()==WashedAshoreGenerationStatus.SEARCHING||!act.hasLayout()){
                act.setStatus(WashedAshoreGenerationStatus.FAILED);
                data.dirty();
                CampaignCore.LOGGER.error("act_location_resolution_failed_before_join attempts={} has_layout={}",
                        act.generationAttempts(),act.hasLayout());
                return false;
            }
            CampaignCore.LOGGER.info("act_locations_resolved_before_join status={} beach={} guide={} graveyard={} settlement={} dark_forest={} crossing={} other_settlement={} raven={}",
                    act.generationStatus(),act.beachSpawn(),act.guideLandmark(),act.undertakerGraveyard(),
                    act.settlement(),act.darkForest(),act.devilsCrossing(),act.otherSettlement(),act.ravenArena());
            return true;
        } catch(RuntimeException ex) {
            act.setStatus(WashedAshoreGenerationStatus.FAILED);
            data.dirty();
            CampaignCore.LOGGER.error("act_location_resolution_exception_before_join",ex);
            return false;
        }
    }
    public static void tick(ServerLevel level,WashedAshoreSavedData data) {
        for(WashedAshoreInstance act:data.instances()){
            if(act.generationStatus()==WashedAshoreGenerationStatus.SEARCHING&&act==data.act())searchOne(level,data);
            else if(act.generationStatus()==WashedAshoreGenerationStatus.PLACING)place(level,data,act);
        }
    }
    private static void searchOne(ServerLevel level,WashedAshoreSavedData data) {
        WashedAshoreInstance act=data.act();act.nextAttempt();var cfg=WashedAshoreConfig.INSTANCE;
        BlockPos origin=level.getSharedSpawnPos().atY(level.getSeaLevel());
        var located=level.findClosestBiome3d(biome->biome.is(BiomeTags.IS_BEACH),origin,
                cfg.beachSearchRadius,32,64);
        BlockPos beach;
        boolean degraded=located==null;
        if(located!=null){
            act.setBeachSearchCenter(located.getFirst());
            beach=surface(level,located.getFirst());
            CampaignCore.LOGGER.info("beach_biome_located origin={} biome_center={} surface={} biome={}",origin,
                    located.getFirst(),beach,located.getSecond().unwrapKey()
                            .map(k->k.location().toString()).orElse("unknown"));
        }else{
            beach=surface(level,level.getSharedSpawnPos().offset(cfg.beachSearchRadius/8,0,0));
            CampaignCore.LOGGER.warn("beach_biome_locator_failed origin={} radius={}; degraded fallback={}",
                    origin,cfg.beachSearchRadius,beach);
        }
        calculateLayout(level,data,act,beach,degraded,true);
        data.dirty();
    }
    private static void calculateLayout(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos beach,boolean degraded,boolean setWorldSpawn) {
        var cfg=WashedAshoreConfig.INSTANCE;Vec3 inland=dominantInland(level,beach);
        Random random=new Random(level.getSeed()^beach.asLong());
        double settlementDistance=cfg.settlementMinimumDistance+random.nextDouble()*(cfg.settlementMaximumDistance-cfg.settlementMinimumDistance);
        BlockPos settlement=resolveDrySite(level,
                BlockPos.containing(Vec3.atCenterOf(beach).add(inland.scale(settlementDistance))),512,16,8);
        double guideDistance=cfg.guideMinimumDistance+random.nextDouble()*(cfg.guideMaximumDistance-cfg.guideMinimumDistance);
        BlockPos guide=resolveDrySite(level,
                BlockPos.containing(Vec3.atCenterOf(beach).add(inland.scale(guideDistance))),24,4,2);
        double progress=cfg.graveyardMinimumProgress+random.nextDouble()*(cfg.graveyardMaximumProgress-cfg.graveyardMinimumProgress);
        Vec3 start=Vec3.atCenterOf(beach),end=Vec3.atCenterOf(settlement),direction=end.subtract(start).normalize();
        Vec3 perpendicular=new Vec3(-direction.z,0,direction.x);
        BlockPos grave=resolveDrySite(level,
                // Reserve half of the corridor allowance for initial variation
                // and half for finding a dry, terrain-fit-capable footprint.
                BlockPos.containing(start.lerp(end,progress).add(perpendicular.scale(random.nextDouble()*128-64))),128,9,3);
        BlockPos raven=resolveDrySite(level,
                BlockPos.containing(Vec3.atCenterOf(settlement).add(perpendicular.scale(cfg.ravenMinimumDistanceFromSettlement))),96,12,4);
        BlockPos forest=findForestedSite(level,settlement,random);
        Vec3 onward=direction.add(perpendicular.scale(random.nextBoolean()?.65:-.65)).normalize();
        BlockPos other=resolveDrySite(level,
                BlockPos.containing(Vec3.atCenterOf(settlement).add(onward.scale(700))),512,16,8);
        BlockPos crossing=resolveDrySite(level,
                BlockPos.containing(Vec3.atCenterOf(settlement).lerp(Vec3.atCenterOf(other),.48)
                .add(new Vec3(-onward.z,0,onward.x).scale(random.nextDouble()*48-24))),128,8,4);
        BlockPos sculk=resolveSculkSurface(level,settlement,forest,random);
        act.setLayout(beach,guide,grave,settlement,raven);
        act.setRegionalLayout(forest,crossing,other);
        act.setSculkSurface(sculk);
        Vec3 guideDirection=Vec3.atCenterOf(guide).subtract(Vec3.atCenterOf(beach));
        float spawnYaw=(float)(Math.atan2(guideDirection.z,guideDirection.x)*180/Math.PI)-90;
        // Match dedicated spawn-point mods: establish the world spawn as soon as
        // the world-level layout is selected, before any player introduction.
        if(setWorldSpawn)level.setDefaultSpawnPos(beach.above(),spawnYaw);
        act.setStatus(WashedAshoreGenerationStatus.PLACING);data.dirty();
        CampaignCore.LOGGER.info("act_layout_selected beach={} guide={} graveyard={} settlement={} forest={} crossing={} other_settlement={} raven={} sculk_surface={} degraded={}",
                beach,guide,grave,settlement,forest,crossing,other,raven,sculk,degraded);
    }
    /**
     * Resolves the Sculk Surface POI: a dry site on the settlement→forest bearing, but further out than
     * the forest itself. If no dry footprint sits beyond the forest in that direction, it falls back to
     * any eligible dry area within a relatively large distance of the settlement, and finally degrades to
     * a generated-ground column so the POI always exists.
     */
    /**
     * Resolves and registers the Sculk Surface for a world whose layout predates this POI (its position
     * was never selected). Runs once — as soon as it sets the position — using the persisted settlement
     * and forest bearings; no-ops when the POI already exists or those anchors are unavailable.
     */
    public static void backfillSculkSurface(ServerLevel level,WashedAshoreSavedData data){
        WashedAshoreInstance act=data.act();
        if(act.sculkSurface()!=null||act.settlement()==null||act.darkForest()==null)return;
        BlockPos sculk=resolveSculkSurface(level,act.settlement(),act.darkForest(),
                new Random(level.getSeed()^act.settlement().asLong()));
        act.setSculkSurface(sculk);
        EncounterManager.registerDefaults(act);
        data.dirty();
        CampaignCore.LOGGER.info("sculk_surface_backfilled settlement={} forest={} sculk={}",act.settlement(),act.darkForest(),sculk);
    }
    private static BlockPos resolveSculkSurface(ServerLevel level,BlockPos settlement,BlockPos forest,Random random){
        Vec3 origin=Vec3.atCenterOf(settlement),toForest=Vec3.atCenterOf(forest).subtract(origin);
        double forestDistance=toForest.length();
        Vec3 bearing=forestDistance<1?new Vec3(1,0,0):toForest.scale(1.0/forestDistance);
        double beyond=forestDistance+SCULK_BEYOND_FOREST+random.nextDouble()*SCULK_BEYOND_JITTER;
        BlockPos beyondForest=BlockPos.containing(origin.add(bearing.scale(beyond)));
        try{
            return resolveDrySite(level,beyondForest,SCULK_SEARCH_RADIUS,SCULK_FOOTPRINT,8);
        }catch(IllegalStateException beyondFailed){
            CampaignCore.LOGGER.warn("sculk_surface_beyond_forest_unresolved requested={} reason={}",beyondForest,beyondFailed.getMessage());
            BlockPos fallbackRequest=BlockPos.containing(origin.add(bearing.scale(SCULK_FALLBACK_DISTANCE)));
            try{
                return resolveDrySite(level,fallbackRequest,SCULK_FALLBACK_SEARCH_RADIUS,SCULK_FOOTPRINT,8);
            }catch(IllegalStateException fallbackFailed){
                CampaignCore.LOGGER.warn("sculk_surface_fallback_unresolved requested={} reason={}; degrading",fallbackRequest,fallbackFailed.getMessage());
                return generatedGround(level,fallbackRequest);
            }
        }
    }
    private static Vec3 dominantInland(ServerLevel level,BlockPos beach) {
        Vec3 best=new Vec3(1,0,0);int bestScore=Integer.MIN_VALUE;
        for(int i=0;i<8;i++){double a=i*Math.PI/4;Vec3 d=new Vec3(Math.cos(a),0,Math.sin(a));int score=0;
            for(int distance:new int[]{32,64,96}){BlockPos p=surface(level,BlockPos.containing(Vec3.atCenterOf(beach).add(d.scale(distance))));
                score+=level.getBlockState(p).is(Blocks.WATER)?-3:1;}
            if(score>bestScore){bestScore=score;best=d;}}
        return best.normalize();
    }
    private static void place(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act) {
        try {
            if(!ensureFrontierHub(level,data,act,act.settlement(),PRIMARY_HUB_PLACED,true))return;
            placeGuide(level,act.guideLandmark(),act.settlement());
            placeExpeditionCache(level,act.guideLandmark(),act.undertakerGraveyard());
            act.setUndertakerGraveyard(placeGraveyard(level,act.undertakerGraveyard(),act.settlement()));
            placeRavenArena(level,act.ravenArena());
            EncounterManager.registerDefaults(act);
            act.setStatus(WashedAshoreGenerationStatus.COMPLETE);
            CampaignCore.LOGGER.info("act_structure_placement_complete instance={}",act.actInstanceId());
        } catch(RuntimeException ex) {
            act.setStatus(WashedAshoreGenerationStatus.DEGRADED);
            EncounterManager.registerDefaults(act);
            CampaignCore.LOGGER.error("act_structure_placement_degraded",ex);
        }
        data.dirty();
    }
    public static String createReinstance(ServerLevel level,WashedAshoreSavedData data,BlockPos requested,boolean bypassBeach){
        int separation=WashedAshoreConfig.INSTANCE.instanceMinimumSeparation;
        for(WashedAshoreInstance existing:data.instances())if(existing.beachSpawn()!=null&&existing.beachSpawn().distSqr(requested)<(long)separation*separation)
            return "Requested origin is too close to instance "+existing.actInstanceId()+" (minimum "+separation+" blocks).";
        BlockPos beach;
        if(bypassBeach)beach=surface(level,requested);
        else{
            var located=level.findClosestBiome3d(biome->biome.is(BiomeTags.IS_BEACH),requested,
                    WashedAshoreConfig.INSTANCE.beachSearchRadius,32,64);
            if(located==null)return "No valid beach was found around the requested origin.";
            beach=surface(level,located.getFirst());
        }
        for(WashedAshoreInstance existing:data.instances())if(existing.beachSpawn()!=null&&existing.beachSpawn().distSqr(beach)<(long)separation*separation)
            return "Resolved beach is too close to instance "+existing.actInstanceId()+" (minimum "+separation+" blocks).";
        // The saved-data model has one canonical primary slot. If it is still empty, populate it
        // instead of creating an additional instance; otherwise the manager continues to see an
        // uninitialized primary act and never advances any naturally bootstrapped storyline.
        WashedAshoreInstance created;
        boolean populatePrimary=data.act().generationStatus()==WashedAshoreGenerationStatus.UNINITIALIZED
                &&!data.act().hasLayout();
        if(populatePrimary)created=data.act();
        else created=new WashedAshoreInstance();
        created.setWorldSpawnStoryline(false);
        created.setStatus(WashedAshoreGenerationStatus.SEARCHING);created.setBeachSearchCenter(requested);
        calculateLayout(level,data,created,beach,bypassBeach,false);
        if(!populatePrimary)data.addInstance(created);
        return null;
    }
    public static void tickDeferredOtherHub(ServerLevel level,WashedAshoreSavedData data){
        for(WashedAshoreInstance act:data.instances())tickDeferredOtherHub(level,data,act);
    }
    private static void tickDeferredOtherHub(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act){
        if(data.act().completedWorldObjectives().contains(OTHER_HUB_REQUESTED))act.completedWorldObjectives().add(OTHER_HUB_REQUESTED);
        if(!act.completedWorldObjectives().contains(OTHER_HUB_REQUESTED))return;
        boolean otherFinished=act.completedWorldObjectives().contains(OTHER_HUB_PLACED)
                ||act.completedWorldObjectives().contains(OTHER_HUB_FAILED);
        if(!otherFinished){
            try{
                otherFinished=ensureFrontierHub(level,data,act,act.otherSettlement(),OTHER_HUB_PLACED,false);
            }catch(RuntimeException ex){
                act.completedWorldObjectives().add(OTHER_HUB_FAILED);
                data.dirty();
                otherFinished=true;
                CampaignCore.LOGGER.error("deferred_other_frontier_hub_failed position={}",act.otherSettlement(),ex);
                for(var player:level.players()){
                    dev.campaigncore.washedashore.message.CampaignMessages.send(player,"world_preparation_degraded");
                }
            }
        }
        // Settlers accepts one asynchronous placement per dimension. Devil's Crossing remains part
        // of the same deferred construction phase, but is enqueued after the inhabited hub ends.
        if(!otherFinished)return;
        if(!act.completedWorldObjectives().contains(DEVILS_CROSSING_RUIN_PLACED)
                &&!act.completedWorldObjectives().contains(DEVILS_CROSSING_RUIN_FAILED)){
            try{
                ensureRuinedFrontierHub(level,data,act,act.devilsCrossing());
            }catch(RuntimeException ex){
                act.completedWorldObjectives().add(DEVILS_CROSSING_RUIN_FAILED);
                data.dirty();
                CampaignCore.LOGGER.error("deferred_devils_crossing_ruin_failed position={}",act.devilsCrossing(),ex);
                for(var player:level.players()){
                    dev.campaigncore.washedashore.message.CampaignMessages.send(player,"world_preparation_degraded");
                }
            }
        }
    }
    private static boolean ensureRuinedFrontierHub(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos requested){
        if(act.completedWorldObjectives().contains(DEVILS_CROSSING_RUIN_PLACED))return true;
        String key=act.actInstanceId()+":"+DEVILS_CROSSING_RUIN_PLACED;
        HubLoadState state=HUB_LOADS.get(key);
        if(state==null||state.level!=level){
            state=new HubLoadState(level,requested);
            HUB_LOADS.put(key,state);
            announceHubConstruction(level,true,DEVILS_CROSSING_RUIN_PLACED,state.center);
            proactiveRelocate(level,state,RUIN_RELOCATION_FOOTPRINT,act::setDevilsCrossing);
        }
        if(!loadHubChunks(level,state,DEVILS_CROSSING_RUIN_PLACED))return false;
        try{
            HubJobResult result=pollOrStartHubJob(level,state,true,DEVILS_CROSSING_RUIN_PLACED);
            if(result==HubJobResult.WAITING)return false;
            if(result!=HubJobResult.ENDED||!state.worldChanged){
                // Settlers refused or no-opped this site (commonly too close to another settlement).
                // Relocate the ruin to a clear spot and retry rather than failing the objective.
                if(relocateHub(level,state,RUIN_RELOCATION_FOOTPRINT,act::setDevilsCrossing))return false;
                throw new IllegalStateException("Settlers ruined frontier hub could not be placed clear of existing settlements after "+state.relocationAttempts+" relocations");
            }
            act.completedWorldObjectives().add(DEVILS_CROSSING_RUIN_PLACED);
            releaseHubTickets(state);HUB_LOADS.remove(key);data.dirty();
            announceHubConstruction(level,false,DEVILS_CROSSING_RUIN_PLACED,state.center);
            CampaignCore.LOGGER.info("devils_crossing_ruined_hub_complete requested={} final={}",requested,state.center);
            return true;
        }catch(RuntimeException ex){
            releaseHubTickets(state);HUB_LOADS.remove(key);throw ex;
        }
    }
    private static boolean ensureFrontierHub(ServerLevel level,WashedAshoreSavedData data,WashedAshoreInstance act,BlockPos requested,
                                             ResourceLocation completionFlag,boolean primary) {
        if(act.completedWorldObjectives().contains(completionFlag))return true;
        String key=act.actInstanceId()+":"+completionFlag;
        HubLoadState state=HUB_LOADS.get(key);
        if(state==null||state.level!=level){
            state=new HubLoadState(level,requested);
            HUB_LOADS.put(key,state);
            announceHubConstruction(level,true,completionFlag,state.center);
            proactiveRelocate(level,state,HUB_RELOCATION_FOOTPRINT,primary?act::setSettlement:act::setOtherSettlement);
        }
        if(!loadHubChunks(level,state,completionFlag))return false;
        try {
            HubJobResult result=pollOrStartHubJob(level,state,false,completionFlag);
            if(result==HubJobResult.WAITING)return false;
            BlockPos actual=result==HubJobResult.ENDED?findPlacedSettlement(level,state.center):null;
            if(actual==null){
                // Settlers refused this site or registered no settlement (commonly too close to another).
                // Relocate to a clear spot and retry rather than failing the objective.
                if(relocateHub(level,state,HUB_RELOCATION_FOOTPRINT,primary?act::setSettlement:act::setOtherSettlement))return false;
                throw new IllegalStateException("Settlers frontier hub could not be placed clear of existing settlements after "+state.relocationAttempts+" relocations");
            }
            if(primary)act.setSettlement(actual);else act.setOtherSettlement(actual);
            act.completedWorldObjectives().add(completionFlag);
            releaseHubTickets(state);
            HUB_LOADS.remove(key);
            data.dirty();
            CampaignCore.LOGGER.info("frontier_hub_story_placement_complete id={} requested={} actual={}",
                    completionFlag,requested,actual);
            announceHubConstruction(level,false,completionFlag,actual);
            return true;
        } catch(RuntimeException ex) {
            releaseHubTickets(state);
            HUB_LOADS.remove(key);
            throw ex;
        }
    }
    private static HubJobResult pollOrStartHubJob(ServerLevel level,HubLoadState state,boolean ruined,
                                                  ResourceLocation id){
        Optional<FrontierHubRuntimePlacer.JobStatus> status=FrontierHubRuntimePlacer.status(level);
        if(state.enqueued){
            if(status.isEmpty()){
                CampaignCore.LOGGER.info("frontier_hub_async_job_ended id={} center={} last_phase={} last_percent={} world_changed={}",
                        id,state.center,state.lastPhase,state.lastPercent,state.worldChanged);
                return HubJobResult.ENDED;
            }
            updateHubJobStatus(state,status.get(),id);
            return HubJobResult.WAITING;
        }
        // A manually-started Settlers command may already own the dimension slot. Wait instead of
        // treating its normal "another placement is active" response as story generation failure.
        if(status.isPresent())return HubJobResult.WAITING;
        CommandSourceStack source=level.getServer().createCommandSourceStack()
                .withLevel(level).withPosition(Vec3.atCenterOf(state.center)).withPermission(4)
                .withSuppressedOutput();
        FrontierHubRuntimePlacer.StartResult start=FrontierHubRuntimePlacer.enqueue(source,state.center,ruined);
        if(!start.success()){
            String message=start.message();
            if(message.contains("already active"))return HubJobResult.WAITING;
            // A site-specific rejection (e.g. too close to another settlement) is recoverable by relocating.
            CampaignCore.LOGGER.warn("frontier_hub_enqueue_rejected id={} center={} message={}",id,state.center,message);
            return HubJobResult.REJECTED;
        }
        state.enqueued=true;
        FrontierHubRuntimePlacer.status(level).ifPresent(started->updateHubJobStatus(state,started,id));
        CampaignCore.LOGGER.info("frontier_hub_async_job_enqueued id={} center={} ruined={}",id,state.center,ruined);
        return HubJobResult.WAITING;
    }
    private static void updateHubJobStatus(HubLoadState state,FrontierHubRuntimePlacer.JobStatus status,ResourceLocation id){
        String phase=status.phase();
        int percent=status.percent();
        boolean changed=status.worldChanged();
        if(!phase.equals(state.lastPhase)||percent/10!=state.lastPercent/10)
            CampaignCore.LOGGER.info("frontier_hub_async_progress id={} phase={} percent={}",id,phase,percent);
        state.lastPhase=phase;state.lastPercent=percent;state.worldChanged|=changed;
    }
    private static List<BlockPos> settlementCenters(ServerLevel level){
        List<BlockPos> centers=new ArrayList<>();
        for(Settlement settlement:SettlementManager.get(level).all())centers.add(settlement.center());
        return centers;
    }
    private static BlockPos findPlacedSettlement(ServerLevel level,BlockPos requested){
        BlockPos nearest=null;double nearestDistance=Double.MAX_VALUE;
        for(BlockPos center:settlementCenters(level)){
            double distance=center.distSqr(requested);
            if(distance<nearestDistance){nearest=center;nearestDistance=distance;}
        }
        return nearestDistance<=256.0D*256.0D?nearest:null;
    }
    private static boolean clearOfSettlements(ServerLevel level,BlockPos candidate,int separation){
        double minimum=(double)separation*separation;
        for(BlockPos center:settlementCenters(level))if(center.distSqr(candidate)<minimum)return false;
        return true;
    }
    /**
     * Fallback for a Settlers placement that was refused or produced nothing: spirals outward from the
     * POI's original requested position to the first dry site clear of every existing settlement (natural
     * or campaign-forced), updates the stored act position via {@code positionSink}, and resets the load
     * state so the next tick retries at the new site. Returns false when relocation is exhausted.
     */
    private static boolean relocateHub(ServerLevel level,HubLoadState state,int footprintRadius,
                                       java.util.function.Consumer<BlockPos> positionSink){
        if(state.relocationAttempts>=MAX_HUB_RELOCATIONS)return false;
        state.relocationAttempts++;
        int separation=WashedAshoreConfig.INSTANCE.settlementSeparation;
        BlockPos origin=state.originalCenter;
        for(int i=1;i<=24;i++){
            double angle=i*2.399963229728653;
            double radius=separation*(1.0+i/24.0*3.0)+(double)state.relocationAttempts*separation;
            BlockPos candidate=generatedGround(level,origin.offset(
                    Mth.floor(Math.cos(angle)*radius),0,Mth.floor(Math.sin(angle)*radius)));
            if(isGeneratedDryArea(level,candidate,footprintRadius,4)&&clearOfSettlements(level,candidate,separation)){
                releaseHubTickets(state);
                state.retarget(candidate);
                positionSink.accept(candidate);
                CampaignCore.LOGGER.info("frontier_hub_relocated attempt={} from={} to={} separation={}",
                        state.relocationAttempts,origin,candidate,separation);
                return true;
            }
        }
        CampaignCore.LOGGER.warn("frontier_hub_relocation_no_site attempt={} origin={} separation={}",
                state.relocationAttempts,origin,separation);
        return false;
    }
    /**
     * One-time check when a hub's load state is created: if the requested site already sits within
     * {@code settlementSeparation} of an existing settlement, relocate before starting a job that
     * Settlers would only refuse.
     */
    private static void proactiveRelocate(ServerLevel level,HubLoadState state,int footprintRadius,
                                          java.util.function.Consumer<BlockPos> positionSink){
        if(!clearOfSettlements(level,state.center,WashedAshoreConfig.INSTANCE.settlementSeparation))
            relocateHub(level,state,footprintRadius,positionSink);
    }
    private static boolean loadHubChunks(ServerLevel level,HubLoadState state,ResourceLocation id){
        int loaded=0;
        while(state.cursor<state.total()&&loaded++<HUB_CHUNKS_PER_TICK){
            int diameter=HUB_CHUNK_RADIUS*2+1;
            int dx=state.cursor%diameter-HUB_CHUNK_RADIUS;
            int dz=state.cursor/diameter-HUB_CHUNK_RADIUS;
            ChunkPos chunk=new ChunkPos((state.center.getX()>>4)+dx,(state.center.getZ()>>4)+dz);
            level.getChunkSource().addRegionTicket(HUB_GENERATION_TICKET,chunk,HUB_TICKET_LEVEL,chunk);
            state.ticketed.add(chunk);
            level.getChunk(chunk.x,chunk.z);
            state.cursor++;
        }
        if(state.cursor<state.total()){
            if(state.cursor==HUB_CHUNKS_PER_TICK||state.cursor%64<2)
                CampaignCore.LOGGER.info("frontier_hub_chunks_loading id={} progress={}/{} center={}",
                        id,state.cursor,state.total(),state.center);
            return false;
        }
        return true;
    }
    private static void releaseHubTickets(HubLoadState state){
        for(ChunkPos chunk:state.ticketed)
            state.level.getChunkSource().removeRegionTicket(HUB_GENERATION_TICKET,chunk,HUB_TICKET_LEVEL,chunk);
        CampaignCore.LOGGER.info("frontier_hub_chunk_tickets_released center={} chunks={}",state.center,state.ticketed.size());
        state.ticketed.clear();
    }
    private static void announceHubConstruction(ServerLevel level,boolean starting,ResourceLocation completionFlag,BlockPos center){
        boolean primary=completionFlag.equals(PRIMARY_HUB_PLACED);
        boolean crossing=completionFlag.equals(DEVILS_CROSSING_RUIN_PLACED);
        String messageId=starting?"hub_construction_started":"hub_construction_finished";
        String nameKey=crossing?"settlement.campaign_core.washed_ashore.devils_crossing":
                primary?"settlement.campaign_core.washed_ashore.primary":"settlement.campaign_core.washed_ashore.secondary";
        Component name=!starting&&!crossing
                ?dev.campaigncore.washedashore.message.SettlementDialogueNames.at(level,center,nameKey)
                :Component.translatable(nameKey);
        for(var player:level.players())dev.campaigncore.washedashore.message.CampaignMessages.send(player,messageId,name);
    }
    private static void placeGuide(ServerLevel l,BlockPos p,BlockPos settlement){
        l.setBlock(p,Blocks.COBBLESTONE.defaultBlockState(),3);l.setBlock(p.above(),Blocks.OAK_PLANKS.defaultBlockState(),3);
        l.setBlock(p.above(2),Blocks.TORCH.defaultBlockState(),3);
        Direction inland=guideInland(p,settlement);
        l.setBlock(p.relative(inland),Blocks.MOSSY_COBBLESTONE.defaultBlockState(),3);
        Direction signFace=inland.getOpposite();
        BlockPos signPos=p.above().relative(signFace);
        l.setBlock(signPos,Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING,signFace),3);
        if(l.getBlockEntity(signPos) instanceof SignBlockEntity sign){
            String directionKey="sign.campaign_core.washed_ashore."+inland.getName();
            SignText text=new SignText()
                    .setMessage(0,Component.translatable("sign.campaign_core.washed_ashore.civilization"))
                    .setMessage(1,Component.translatable(directionKey));
            sign.setText(text,true);
            sign.setChanged();
        }
    }
    private static Direction guideInland(BlockPos guide,BlockPos settlement){
        Vec3 d=Vec3.atCenterOf(settlement).subtract(Vec3.atCenterOf(guide)).normalize();
        return Math.abs(d.x)>Math.abs(d.z)?(d.x>0?Direction.EAST:Direction.WEST):(d.z>0?Direction.SOUTH:Direction.NORTH);
    }
    /** The wall-sign block on the guide post (client draws the guide glow box here); mirrors placeGuide. */
    public static BlockPos guideSignPos(BlockPos guide,BlockPos settlement){
        return guide.above().relative(guideInland(guide,settlement).getOpposite());
    }
    /**
     * Three adjacent barrels form one expedition cache. They sit only three blocks
     * beyond the guide, on the route toward the Undertaker, and make the kit choice
     * visible without requiring a custom client screen.
     */
    private static void placeExpeditionCache(ServerLevel level,BlockPos guide,BlockPos graveyard){
        Direction forward=guideInland(guide,graveyard);
        Direction side=forward.getClockWise();
        String[] kits={"melee","ranged","magic"};
        for(int i=0;i<kits.length;i++){
            BlockPos requested=guide.relative(forward,3).relative(side,i-1);
            BlockPos ground=dryGround(level,requested);
            BlockPos barrelPos=ground.above();
            level.setBlock(barrelPos,Blocks.BARREL.defaultBlockState(),3);
            if(level.getBlockEntity(barrelPos) instanceof BarrelBlockEntity barrel){
                CompoundTag barrelData=barrel.saveWithoutMetadata(level.registryAccess());
                barrelData.putString("CustomName","{\"translate\":\"container.campaign_core.expedition_cache."+kits[i]+"\"}");
                barrel.loadWithComponents(barrelData,level.registryAccess());
                // Assign the kit loot table instead of pre-filling items, so per-player loot mods
                // (Lootr and similar) hand each player their own copy. The tables are deterministic
                // (one guaranteed pool per item), so vanilla still yields exactly the listed kit.
                ResourceKey<LootTable> table=ResourceKey.create(Registries.LOOT_TABLE,
                        CampaignCore.washedAshoreId("expedition_cache/"+kits[i]));
                barrel.setLootTable(table,level.getSeed()^barrelPos.asLong());
                barrel.setChanged();
            }
        }
        CampaignCore.LOGGER.info("expedition_cache_placed guide={} toward={} distance=3",guide,graveyard);
    }
    private static BlockPos placeGraveyard(ServerLevel l,BlockPos c,BlockPos toward){
        int targetY=graveyardTargetY(l,c);
        flattenGraveyard(l,c,targetY);
        BlockPos center=new BlockPos(c.getX(),targetY,c.getZ());
        int routeX=toward.getX()-center.getX();
        int routeZ=toward.getZ()-center.getZ();
        boolean routeAlongX=Math.abs(routeX)>=Math.abs(routeZ);
        for(int x=-5;x<=5;x++)for(int z=-4;z<=4;z++){
            BlockPos p=center.offset(x,0,z);
            boolean boundary=Math.abs(x)==5||Math.abs(z)==4;
            // Two opposing three-block openings make the graveyard part of the obvious inland
            // route rather than a sealed obstacle. Their axis follows the settlement corridor.
            boolean entrance=routeAlongX?Math.abs(x)==5&&Math.abs(z)<=1:
                    Math.abs(z)==4&&Math.abs(x)<=1;
            boolean path=routeAlongX?Math.abs(z)<=1:Math.abs(x)<=1;
            if(boundary&&!entrance)l.setBlock(p.above(),Blocks.COBBLESTONE_WALL.defaultBlockState(),3);
            else{
                l.setBlock(p,path?Blocks.COARSE_DIRT.defaultBlockState():
                        ((x+z)&3)==0?Blocks.COARSE_DIRT.defaultBlockState():Blocks.GRASS_BLOCK.defaultBlockState(),3);
                if(entrance){
                    l.setBlock(p.above(),Blocks.AIR.defaultBlockState(),3);
                    l.setBlock(p.above(2),Blocks.AIR.defaultBlockState(),3);
                }
            }
        }
        for(int x:new int[]{-3,0,3})for(int z:new int[]{-2,1}){
            if(routeAlongX&&Math.abs(z)<=1||!routeAlongX&&Math.abs(x)<=1)continue;
            BlockPos p=center.offset(x,0,z);
            l.setBlock(p.above(),Blocks.COBBLESTONE.defaultBlockState(),3);
            l.setBlock(p.above(2),Blocks.STONE_BRICK_SLAB.defaultBlockState(),3);
        }
        BlockPos lantern=routeAlongX?center.offset(0,1,3):center.offset(3,1,0);
        l.setBlock(lantern,Blocks.SOUL_LANTERN.defaultBlockState(),3);
        CampaignCore.LOGGER.info("graveyard_terrain_fitted original={} center={} target_y={} entrance_axis={}",
                c,center,targetY,routeAlongX?"east_west":"north_south");
        return center;
    }
    private static int graveyardTargetY(ServerLevel level,BlockPos center){
        List<Integer> heights=new ArrayList<>();
        for(int x=-5;x<=5;x+=2)for(int z=-4;z<=4;z+=2)heights.add(naturalGroundY(level,center.getX()+x,center.getZ()+z));
        Collections.sort(heights);
        return heights.get(heights.size()/2);
    }
    private static int naturalGroundY(ServerLevel level,int x,int z){
        level.getChunk(x>>4,z>>4);
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1;
        BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos(x,y,z);
        while(y>level.getMinBuildHeight()+1){
            var state=level.getBlockState(cursor.set(x,y,z));
            if(!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!state.getCollisionShape(level,cursor).isEmpty())break;
            y--;
        }
        return y;
    }
    private static void flattenGraveyard(ServerLevel level,BlockPos center,int targetY){
        for(int x=-7;x<=7;x++)for(int z=-6;z<=6;z++){
            int current=naturalGroundY(level,center.getX()+x,center.getZ()+z);
            int edge=Math.max(Math.max(0,Math.abs(x)-5),Math.max(0,Math.abs(z)-4));
            int desired=edge==0?targetY:Mth.floor(Mth.lerp(Math.min(1.0,edge/3.0),targetY,current));
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,center.getX()+x,center.getZ()+z)+1;
            BlockPos.MutableBlockPos cursor=new BlockPos.MutableBlockPos();
            for(int y=desired+1;y<=top;y++)level.setBlock(cursor.set(center.getX()+x,y,center.getZ()+z),Blocks.AIR.defaultBlockState(),2);
            for(int y=Math.min(current,desired);y<desired;y++)
                level.setBlock(cursor.set(center.getX()+x,y,center.getZ()+z),Blocks.DIRT.defaultBlockState(),2);
            level.setBlock(cursor.set(center.getX()+x,desired,center.getZ()+z),Blocks.GRASS_BLOCK.defaultBlockState(),2);
        }
    }
    private static void placeRavenArena(ServerLevel l,BlockPos c){
        for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++)if(x*x+z*z<=64)l.setBlock(c.offset(x,0,z),Blocks.DEEPSLATE_TILES.defaultBlockState(),3);
        l.setBlock(c.above(),Blocks.SCULK_CATALYST.defaultBlockState(),3);
    }
    private static BlockPos surface(ServerLevel level,BlockPos pos){
        // Unprepared chunks can expose an empty heightmap and produce a below-world Y.
        // Search is staged, so forcing only this candidate chunk remains bounded.
        level.getChunk(pos.getX() >> 4,pos.getZ() >> 4);
        int feetY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
        return new BlockPos(pos.getX(),feetY-1,pos.getZ());
    }
    /**
     * Resolves the Dread POI. The dread event only builds inside a {@code minecraft:is_forest} biome
     * (see the encounter's {@code biome_tag}), so this must return a forest site or the quest can never
     * progress there. A scattered forest clearing near the settlement is preferred; failing that, the
     * nearest forest is located exactly as {@code /locate biome} does to guarantee dread-eligibility.
     */
    private static BlockPos findForestedSite(ServerLevel level,BlockPos settlement,Random random){
        for(int i=0;i<24;i++){
            double angle=random.nextDouble()*Math.PI*2,distance=180+random.nextDouble()*220;
            BlockPos candidate=generatedGround(level,settlement.offset(Mth.floor(Math.cos(angle)*distance),0,Mth.floor(Math.sin(angle)*distance)));
            if(isForest(level,candidate)&&isGeneratedDryArea(level,candidate,12,4))return candidate;
        }
        var located=level.findClosestBiome3d(biome->biome.is(BiomeTags.IS_FOREST),settlement.atY(level.getSeaLevel()),6400,32,64);
        if(located!=null){
            BlockPos forest=dryForestNear(level,located.getFirst());
            if(forest!=null){
                CampaignCore.LOGGER.info("dread_poi_forest_located settlement={} biome_center={} site={}",settlement,located.getFirst(),forest);
                return forest;
            }
        }
        // No forest anywhere in range: such a world cannot host the dread event regardless of placement.
        CampaignCore.LOGGER.warn("dread_poi_no_forest_in_range settlement={}; dread event may be ineligible at fallback",settlement);
        return resolveDrySite(level,settlement.offset(240,0,0),96,12,4);
    }
    private static boolean isForest(ServerLevel level,BlockPos pos){
        return level.getUncachedNoiseBiome(pos.getX()>>2,pos.getY()>>2,pos.getZ()>>2).is(BiomeTags.IS_FOREST);
    }
    /** A dry, buildable forest column near the located biome center, falling back to any forest column
     *  there (dread-eligibility outranks a perfectly dry footprint). Null only if none is forest. */
    private static BlockPos dryForestNear(ServerLevel level,BlockPos located){
        BlockPos forestFallback=null;
        for(int i=0;i<24;i++){
            double angle=i*2.399963229728653,radius=i==0?0:Math.min(160,8+i*6.0);
            BlockPos candidate=generatedGround(level,located.offset(Mth.floor(Math.cos(angle)*radius),0,Mth.floor(Math.sin(angle)*radius)));
            if(!isForest(level,candidate))continue;
            if(forestFallback==null)forestFallback=candidate;
            if(isGeneratedDryArea(level,candidate,12,4))return candidate;
        }
        return forestFallback;
    }
    private static BlockPos resolveDrySite(ServerLevel level,BlockPos requested,int searchRadius,int footprintRadius,int sampleStep){
        BlockPos first=generatedGround(level,requested);
        if(isGeneratedDryArea(level,first,footprintRadius,sampleStep))return first;
        // Large structure searches may cover a wide area, but candidate scoring
        // remains noise-only and strictly capped.
        int attempts=Math.max(12,Math.min(24,searchRadius/20));
        for(int i=1;i<=attempts;i++){
            double angle=i*2.399963229728653;
            double radius=searchRadius*Math.sqrt(i/(double)attempts);
            BlockPos candidate=generatedGround(level,requested.offset(
                    Mth.floor(Math.cos(angle)*radius),0,Mth.floor(Math.sin(angle)*radius)));
            if(isGeneratedDryArea(level,candidate,footprintRadius,sampleStep)){
                CampaignCore.LOGGER.info("dry_land_site_adjusted requested={} selected={} footprint_radius={}",
                        requested,candidate,footprintRadius);
                return candidate;
            }
        }
        throw new IllegalStateException("No dry land found near "+requested+" for footprint radius "+footprintRadius);
    }
    private static boolean isDryArea(ServerLevel level,BlockPos center,int radius,int step){
        int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE;
        for(int x=-radius;x<=radius;x+=step)for(int z=-radius;z<=radius;z+=step){
            BlockPos ground=dryGround(level,center.offset(x,0,z));
            if(!level.getBlockState(ground).isSolid()
                    ||!level.getFluidState(ground).isEmpty()
                    ||!level.getFluidState(ground.above()).isEmpty())return false;
            minimum=Math.min(minimum,ground.getY());
            maximum=Math.max(maximum,ground.getY());
        }
        // Prevent large authored footprints from being requested against cliffs.
        return maximum-minimum<=allowedSlope(radius);
    }
    private static BlockPos dryGround(ServerLevel level,BlockPos position){
        return new BlockPos(position.getX(),naturalGroundY(level,position.getX(),position.getZ()),position.getZ());
    }
    private static BlockPos generatedGround(ServerLevel level,BlockPos position){
        var source=level.getChunkSource();
        int y=source.getGenerator().getBaseHeight(position.getX(),position.getZ(),
                Heightmap.Types.WORLD_SURFACE_WG,level,source.randomState())-1;
        return new BlockPos(position.getX(),y,position.getZ());
    }
    private static boolean isGeneratedDryArea(ServerLevel level,BlockPos center,int radius,int step){
        var source=level.getChunkSource();
        var generator=source.getGenerator();
        int minimum=Integer.MAX_VALUE,maximum=Integer.MIN_VALUE;
        // Candidate scoring is deliberately coarse. A selected candidate still
        // receives the full loaded-block footprint check below.
        int coarseStep=Math.max(step,Math.max(4,radius));
        for(int x=-radius;x<=radius;x+=coarseStep)for(int z=-radius;z<=radius;z+=coarseStep){
            int worldX=center.getX()+x,worldZ=center.getZ()+z;
            int surface=generator.getBaseHeight(worldX,worldZ,Heightmap.Types.WORLD_SURFACE_WG,level,source.randomState());
            int oceanFloor=generator.getBaseHeight(worldX,worldZ,Heightmap.Types.OCEAN_FLOOR_WG,level,source.randomState());
            // A surface above the ocean floor indicates a water column.
            if(surface>oceanFloor)return false;
            int ground=surface-1;
            minimum=Math.min(minimum,ground);maximum=Math.max(maximum,ground);
        }
        return maximum-minimum<=allowedSlope(radius);
    }
    private static int allowedSlope(int footprintRadius){
        // Small authored sites (especially the graveyard) have their own
        // bounded cut/fill pass. Large hubs stay comparatively strict.
        return footprintRadius<=10?12:Math.max(6,footprintRadius/3);
    }
    public static boolean liesBetween(BlockPos start,BlockPos middle,BlockPos end,double maxLateralDistance){
        Vec3 a=Vec3.atCenterOf(start),b=Vec3.atCenterOf(middle),c=Vec3.atCenterOf(end),ac=c.subtract(a);
        double t=b.subtract(a).dot(ac)/ac.lengthSqr();if(t<=0||t>=1)return false;
        return b.distanceTo(a.add(ac.scale(t)))<=maxLateralDistance;
    }
    private static final class HubLoadState {
        private final ServerLevel level;
        private BlockPos center;
        private final BlockPos originalCenter;
        private final Set<ChunkPos> ticketed=new HashSet<>();
        private int cursor;
        private int relocationAttempts;
        private boolean enqueued;
        private boolean worldChanged;
        private String lastPhase="not_started";
        private int lastPercent=-1;
        private HubLoadState(ServerLevel level,BlockPos center){this.level=level;this.center=center;this.originalCenter=center;}
        private int total(){int diameter=HUB_CHUNK_RADIUS*2+1;return diameter*diameter;}
        /** Re-points a rejected job at a new site and clears its per-attempt progress for a fresh run. */
        private void retarget(BlockPos newCenter){
            center=newCenter;cursor=0;enqueued=false;worldChanged=false;lastPhase="not_started";lastPercent=-1;
        }
    }
}
