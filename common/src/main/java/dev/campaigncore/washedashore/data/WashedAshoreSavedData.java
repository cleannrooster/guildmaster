package dev.campaigncore.washedashore.data;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.compat.LegacyCampaignCompatibility;
import dev.campaigncore.washedashore.act.*;
import dev.campaigncore.washedashore.encounter.*;
import dev.campaigncore.washedashore.incident.HubIncidentState;
import dev.campaigncore.washedashore.incident.HubIncidentKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class WashedAshoreSavedData extends SavedData {
    // Compatibility key: existing worlds store the live Washed Ashore state here.
    private static final String DATA_NAME=LegacyCampaignCompatibility.SAVED_DATA_KEY;
    private final WashedAshoreInstance act=new WashedAshoreInstance();
    private final List<WashedAshoreInstance> additionalInstances=new ArrayList<>();
    private final Map<UUID,WashedAshoreProgress> players=new HashMap<>();
    private final Map<HubIncidentKey,HubIncidentState> hubIncidents=new LinkedHashMap<>();
    private final Set<Long> processedNaturalAnchors=new HashSet<>();

    public static WashedAshoreSavedData get(ServerLevel level) {
        ServerLevel overworld=level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WashedAshoreSavedData::new,WashedAshoreSavedData::load,null),DATA_NAME);
    }
    public WashedAshoreInstance act(){return act;}
    public List<WashedAshoreInstance> instances(){List<WashedAshoreInstance> values=new ArrayList<>(1+additionalInstances.size());values.add(act);values.addAll(additionalInstances);return List.copyOf(values);}
    /** Returns the persisted layout that owns an encounter object, including reinstanced layouts. */
    public WashedAshoreInstance instanceFor(EncounterAnchor encounter){
        for(WashedAshoreInstance instance:instances())
            if(instance.encounters().containsValue(encounter))return instance;
        return null;
    }
    public void addInstance(WashedAshoreInstance instance){additionalInstances.add(instance);setDirty();}
    public void clearAdditionalInstances(){additionalInstances.clear();setDirty();}
    public void clearHubIncidents(){hubIncidents.clear();setDirty();}
    public WashedAshoreProgress player(UUID id){return players.computeIfAbsent(id,key->{setDirty();return new WashedAshoreProgress();});}
    public Map<UUID,WashedAshoreProgress> playersView(){return Collections.unmodifiableMap(players);}
    public HubIncidentState hubIncident(UUID instanceId,ResourceLocation hub){return hubIncidents.computeIfAbsent(new HubIncidentKey(instanceId,hub),key->{setDirty();return new HubIncidentState();});}
    public Map<HubIncidentKey,HubIncidentState> hubIncidentsView(){return Collections.unmodifiableMap(hubIncidents);}
    public void clearPlayers(){players.clear();setDirty();}
    /** Swaps in a replacement progress entry (prestige wipe); the caller decides what carries over. */
    public void replacePlayer(UUID id,WashedAshoreProgress progress){players.put(id,progress);setDirty();}
    public boolean naturalAnchorProcessed(BlockPos pos){return processedNaturalAnchors.contains(pos.asLong());}
    public void markNaturalAnchorProcessed(BlockPos pos){if(processedNaturalAnchors.add(pos.asLong()))setDirty();}
    public void dirty(){setDirty();}

    private static WashedAshoreSavedData load(CompoundTag tag,HolderLookup.Provider registries) {
        WashedAshoreSavedData data=new WashedAshoreSavedData();
        try {
            CompoundTag a=tag.getCompound("act");
            UUID id=a.hasUUID("instance_id")?a.getUUID("instance_id"):UUID.randomUUID();
            WashedAshoreGenerationStatus status=parseEnum(WashedAshoreGenerationStatus.class,a.getString("status"),WashedAshoreGenerationStatus.DEGRADED);
            data.act.restore(id,status,a.getInt("layout_version"),a.getInt("attempts"));
            // Primary layouts saved before provenance existed were world-spawn layouts.
            data.act.setWorldSpawnStoryline(!a.contains("world_spawn_storyline",Tag.TAG_BYTE)
                    ||a.getBoolean("world_spawn_storyline"));
            data.act.setLayout(readPos(a,"beach"),readPos(a,"guide"),readPos(a,"graveyard"),readPos(a,"settlement"),readPos(a,"raven"));
            data.act.setRegionalLayout(readPos(a,"dark_forest"),readPos(a,"devils_crossing"),readPos(a,"other_settlement"));
            data.act.setSculkSurface(readPos(a,"sculk_surface"));
            ListTag encounters=a.getList("encounters",Tag.TAG_COMPOUND);
            for(int i=0;i<encounters.size();i++){
                CompoundTag e=encounters.getCompound(i);
                ResourceLocation encounter=normalize(ResourceLocation.tryParse(e.getString("id")));
                ResourceLocation boss=ResourceLocation.tryParse(e.getString("boss"));
                BlockPos anchor=readPos(e,"anchor"),spawn=readPos(e,"spawn");
                if(encounter==null||boss==null||anchor==null||spawn==null) continue;
                int retryDelay=e.contains("retry_delay",Tag.TAG_INT)?e.getInt("retry_delay"):EncounterDefinition.DEFAULT_RETRY_DELAY_TICKS;
                EncounterAnchor value=new EncounterAnchor(encounter,boss,anchor,spawn,e.getInt("activation"),e.getInt("reset"),
                        e.getBoolean("one_shot"),parseEnum(WashedAshoreStage.class,e.getString("required_stage"),WashedAshoreStage.NOT_STARTED),retryDelay);
                UUID bossUuid=e.hasUUID("active_boss")?e.getUUID("active_boss"):null;
                ResourceLocation candidate=e.contains("selected_candidate",Tag.TAG_STRING)?ResourceLocation.tryParse(e.getString("selected_candidate")):null;
                value.restore(parseEnum(EncounterStatus.class,e.getString("status"),EncounterStatus.DORMANT),bossUuid,e.getLong("retry_at"),e.getLong("no_players_since"),candidate);
                if(e.hasUUID("pending_removal_boss"))value.restorePendingRemovalBoss(e.getUUID("pending_removal_boss"));
                data.act.encounters().put(encounter,value);
            }
            parseIds(a.getString("objectives"),data.act.completedWorldObjectives());
            repairRelocatedCrossingAnchor(data.act);
            repairRelocatedOtherSettlementAnchor(data.act);
            data.act.setCrossingHordeWave(a.getInt("crossing_horde_wave"));
            data.act.setSculkMobDeaths(a.getInt("sculk_mob_deaths"));
            data.act.setSculkEncounterStartTick(a.getLong("sculk_start_tick"));
            data.act.setSculkWavesSpawned(a.getInt("sculk_waves"));
            if(a.hasUUID("sculk_raven"))data.act.setSculkRaven(a.getUUID("sculk_raven"));
            if(a.hasUUID("sculk_prestige_invoker"))data.act.setSculkPrestigeInvoker(a.getUUID("sculk_prestige_invoker"));
            data.act.setSettlementRaidStartTick(a.contains("settlement_raid_start_tick",Tag.TAG_LONG)
                    ?a.getLong("settlement_raid_start_tick"):-1);
            data.act.setSettlementRaidInitialCount(a.getInt("settlement_raid_initial_count"));
            ResourceLocation primaryHub=CampaignCore.washedAshoreId("primary_frontier_hub_placed");
            if(data.act().hasLayout()
                    && (data.act.generationStatus()==WashedAshoreGenerationStatus.COMPLETE
                    || data.act.generationStatus()==WashedAshoreGenerationStatus.DEGRADED)
                    && !data.act.completedWorldObjectives().contains(primaryHub)){
                data.act.setStatus(WashedAshoreGenerationStatus.PLACING);
                data.act.setLayoutVersion(2);
                data.setDirty();
                CampaignCore.LOGGER.info("act_layout_migrated_to_frontier_hubs instance={}",data.act.actInstanceId());
            }
            CompoundTag p=tag.getCompound("players");
            for(String key:p.getAllKeys()) try{data.players.put(UUID.fromString(key),WashedAshoreProgress.load(p.getCompound(key)));}catch(IllegalArgumentException ignored){}
            if(tag.contains("hub_incidents_by_instance",Tag.TAG_LIST)){
                ListTag incidents=tag.getList("hub_incidents_by_instance",Tag.TAG_COMPOUND);
                for(int i=0;i<incidents.size();i++){
                    CompoundTag entry=incidents.getCompound(i);
                    ResourceLocation hub=ResourceLocation.tryParse(entry.getString("hub"));
                    if(entry.hasUUID("instance")&&hub!=null)
                        data.hubIncidents.put(new HubIncidentKey(entry.getUUID("instance"),hub),HubIncidentState.load(entry.getCompound("state")));
                }
            }else{
                // Legacy state was keyed by hub type alone; it belonged to the only (primary)
                // storyline instance that older versions supported.
                CompoundTag incidents=tag.getCompound("hub_incidents");
                for(String key:incidents.getAllKeys()){
                    ResourceLocation hub=ResourceLocation.tryParse(key);
                    if(hub!=null)data.hubIncidents.put(new HubIncidentKey(data.act.actInstanceId(),hub),HubIncidentState.load(incidents.getCompound(key)));
                }
            }
            ListTag layouts=tag.getList("additional_instances",Tag.TAG_COMPOUND);
            for(int i=0;i<layouts.size();i++)data.additionalInstances.add(loadAdditionalInstance(layouts.getCompound(i)));
            long[] anchors=tag.getLongArray("processed_natural_anchors");for(long anchor:anchors)data.processedNaturalAnchors.add(anchor);
            if(!data.act.hasLayout()&&status==WashedAshoreGenerationStatus.COMPLETE)data.act.setStatus(WashedAshoreGenerationStatus.DEGRADED);
            CampaignCore.LOGGER.info("act_saved_data_loaded instance={} status={} encounters={} players={}",
                    data.act.actInstanceId(),data.act.generationStatus(),data.act.encounters().size(),data.players.size());
        } catch(RuntimeException ex) {
            data.act.setStatus(WashedAshoreGenerationStatus.DEGRADED);
            CampaignCore.LOGGER.error("act_saved_data_validation_failed",ex);
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
        CompoundTag a=new CompoundTag();
        a.putUUID("instance_id",act.actInstanceId());a.putString("status",act.generationStatus().name());
        a.putInt("layout_version",act.layoutVersion());a.putInt("attempts",act.generationAttempts());
        a.putBoolean("world_spawn_storyline",act.worldSpawnStoryline());
        putPos(a,"beach",act.beachSpawn());putPos(a,"guide",act.guideLandmark());putPos(a,"graveyard",act.undertakerGraveyard());
        putPos(a,"settlement",act.settlement());putPos(a,"raven",act.ravenArena());
        putPos(a,"dark_forest",act.darkForest());putPos(a,"devils_crossing",act.devilsCrossing());putPos(a,"other_settlement",act.otherSettlement());
        putPos(a,"sculk_surface",act.sculkSurface());
        ListTag encounters=new ListTag();
        for(EncounterAnchor e:act.encounters().values()){
            CompoundTag n=new CompoundTag();n.putString("id",e.encounterId().toString());n.putString("boss",e.bossEntityType().toString());
            putPos(n,"anchor",e.anchorPos());putPos(n,"spawn",e.bossSpawnPos());n.putInt("activation",e.activationRadius());
            n.putInt("reset",e.resetRadius());n.putBoolean("one_shot",e.oneShot());n.putString("required_stage",e.requiredStage().name());
            n.putInt("retry_delay",e.retryDelayTicks());if(e.retryAt()!=0)n.putLong("retry_at",e.retryAt());
            if(e.noPlayersSince()!=0)n.putLong("no_players_since",e.noPlayersSince());
            n.putString("status",e.status().name());if(e.activeBossUuid()!=null)n.putUUID("active_boss",e.activeBossUuid());
            if(e.pendingRemovalBossUuid()!=null)n.putUUID("pending_removal_boss",e.pendingRemovalBossUuid());
            if(e.selectedCandidate()!=null)n.putString("selected_candidate",e.selectedCandidate().toString());encounters.add(n);
        }
        a.put("encounters",encounters);
        a.putString("objectives",act.completedWorldObjectives().stream().map(ResourceLocation::toString).sorted().reduce("",(x,y)->x+(x.isEmpty()?"":";")+y));
        if(act.crossingHordeWave()>0)a.putInt("crossing_horde_wave",act.crossingHordeWave());
        if(act.sculkMobDeaths()>0)a.putInt("sculk_mob_deaths",act.sculkMobDeaths());
        if(act.sculkEncounterStartTick()>0)a.putLong("sculk_start_tick",act.sculkEncounterStartTick());
        if(act.sculkWavesSpawned()>0)a.putInt("sculk_waves",act.sculkWavesSpawned());
        if(act.sculkRaven()!=null)a.putUUID("sculk_raven",act.sculkRaven());
        if(act.sculkPrestigeInvoker()!=null)a.putUUID("sculk_prestige_invoker",act.sculkPrestigeInvoker());
        if(act.settlementRaidStartTick()>=0)a.putLong("settlement_raid_start_tick",act.settlementRaidStartTick());
        if(act.settlementRaidInitialCount()>0)a.putInt("settlement_raid_initial_count",act.settlementRaidInitialCount());
        tag.put("act",a);
        CompoundTag p=new CompoundTag();players.forEach((id,progress)->p.put(id.toString(),progress.save()));tag.put("players",p);
        ListTag incidents=new ListTag();hubIncidents.forEach((key,state)->{CompoundTag entry=new CompoundTag();entry.putUUID("instance",key.instanceId());entry.putString("hub",key.hubId().toString());entry.put("state",state.save());incidents.add(entry);});tag.put("hub_incidents_by_instance",incidents);
        ListTag layouts=new ListTag();for(WashedAshoreInstance instance:additionalInstances)layouts.add(saveAdditionalInstance(instance));tag.put("additional_instances",layouts);
        tag.putLongArray("processed_natural_anchors",processedNaturalAnchors.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }
    private static CompoundTag saveAdditionalInstance(WashedAshoreInstance act){
        CompoundTag a=new CompoundTag();a.putUUID("instance_id",act.actInstanceId());a.putString("status",act.generationStatus().name());a.putInt("layout_version",act.layoutVersion());a.putInt("attempts",act.generationAttempts());a.putBoolean("world_spawn_storyline",act.worldSpawnStoryline());
        putPos(a,"beach",act.beachSpawn());putPos(a,"guide",act.guideLandmark());putPos(a,"graveyard",act.undertakerGraveyard());putPos(a,"settlement",act.settlement());putPos(a,"raven",act.ravenArena());
        putPos(a,"dark_forest",act.darkForest());putPos(a,"devils_crossing",act.devilsCrossing());putPos(a,"other_settlement",act.otherSettlement());putPos(a,"sculk_surface",act.sculkSurface());
        ListTag encounters=new ListTag();for(EncounterAnchor e:act.encounters().values()){CompoundTag n=new CompoundTag();n.putString("id",e.encounterId().toString());n.putString("boss",e.bossEntityType().toString());putPos(n,"anchor",e.anchorPos());putPos(n,"spawn",e.bossSpawnPos());n.putInt("activation",e.activationRadius());n.putInt("reset",e.resetRadius());n.putBoolean("one_shot",e.oneShot());n.putString("required_stage",e.requiredStage().name());n.putInt("retry_delay",e.retryDelayTicks());n.putString("status",e.status().name());if(e.activeBossUuid()!=null)n.putUUID("active_boss",e.activeBossUuid());if(e.selectedCandidate()!=null)n.putString("selected_candidate",e.selectedCandidate().toString());if(e.retryAt()>0)n.putLong("retry_at",e.retryAt());encounters.add(n);}a.put("encounters",encounters);
        a.putString("objectives",act.completedWorldObjectives().stream().map(ResourceLocation::toString).sorted().reduce("",(x,y)->x+(x.isEmpty()?"":";")+y));
        if(act.crossingHordeWave()>0)a.putInt("crossing_horde_wave",act.crossingHordeWave());
        if(act.sculkMobDeaths()>0)a.putInt("sculk_mob_deaths",act.sculkMobDeaths());
        if(act.sculkEncounterStartTick()>0)a.putLong("sculk_start_tick",act.sculkEncounterStartTick());
        if(act.sculkWavesSpawned()>0)a.putInt("sculk_waves",act.sculkWavesSpawned());
        if(act.sculkRaven()!=null)a.putUUID("sculk_raven",act.sculkRaven());
        if(act.sculkPrestigeInvoker()!=null)a.putUUID("sculk_prestige_invoker",act.sculkPrestigeInvoker());
        if(act.settlementRaidStartTick()>=0)a.putLong("settlement_raid_start_tick",act.settlementRaidStartTick());
        if(act.settlementRaidInitialCount()>0)a.putInt("settlement_raid_initial_count",act.settlementRaidInitialCount());
        return a;
    }
    private static WashedAshoreInstance loadAdditionalInstance(CompoundTag a){
        WashedAshoreInstance act=new WashedAshoreInstance();act.restore(a.hasUUID("instance_id")?a.getUUID("instance_id"):UUID.randomUUID(),parseEnum(WashedAshoreGenerationStatus.class,a.getString("status"),WashedAshoreGenerationStatus.DEGRADED),a.getInt("layout_version"),a.getInt("attempts"));act.setWorldSpawnStoryline(a.contains("world_spawn_storyline",Tag.TAG_BYTE)&&a.getBoolean("world_spawn_storyline"));
        act.setLayout(readPos(a,"beach"),readPos(a,"guide"),readPos(a,"graveyard"),readPos(a,"settlement"),readPos(a,"raven"));act.setRegionalLayout(readPos(a,"dark_forest"),readPos(a,"devils_crossing"),readPos(a,"other_settlement"));act.setSculkSurface(readPos(a,"sculk_surface"));
        ListTag encounters=a.getList("encounters",Tag.TAG_COMPOUND);for(int i=0;i<encounters.size();i++){CompoundTag e=encounters.getCompound(i);ResourceLocation id=normalize(ResourceLocation.tryParse(e.getString("id"))),boss=ResourceLocation.tryParse(e.getString("boss"));BlockPos anchor=readPos(e,"anchor"),spawn=readPos(e,"spawn");if(id==null||boss==null||anchor==null||spawn==null)continue;int retryDelay=e.contains("retry_delay",Tag.TAG_INT)?e.getInt("retry_delay"):EncounterDefinition.DEFAULT_RETRY_DELAY_TICKS;EncounterAnchor value=new EncounterAnchor(id,boss,anchor,spawn,e.getInt("activation"),e.getInt("reset"),e.getBoolean("one_shot"),parseEnum(WashedAshoreStage.class,e.getString("required_stage"),WashedAshoreStage.NOT_STARTED),retryDelay);value.restore(parseEnum(EncounterStatus.class,e.getString("status"),EncounterStatus.DORMANT),e.hasUUID("active_boss")?e.getUUID("active_boss"):null,e.getLong("retry_at"),e.getLong("no_players_since"),ResourceLocation.tryParse(e.getString("selected_candidate")));if(e.hasUUID("pending_removal_boss"))value.restorePendingRemovalBoss(e.getUUID("pending_removal_boss"));act.encounters().put(id,value);}parseIds(a.getString("objectives"),act.completedWorldObjectives());repairRelocatedCrossingAnchor(act);repairRelocatedOtherSettlementAnchor(act);
        act.setCrossingHordeWave(a.getInt("crossing_horde_wave"));
        act.setSculkMobDeaths(a.getInt("sculk_mob_deaths"));
        act.setSculkEncounterStartTick(a.getLong("sculk_start_tick"));
        act.setSculkWavesSpawned(a.getInt("sculk_waves"));
        if(a.hasUUID("sculk_raven"))act.setSculkRaven(a.getUUID("sculk_raven"));
        if(a.hasUUID("sculk_prestige_invoker"))act.setSculkPrestigeInvoker(a.getUUID("sculk_prestige_invoker"));
        act.setSettlementRaidStartTick(a.contains("settlement_raid_start_tick",Tag.TAG_LONG)?a.getLong("settlement_raid_start_tick"):-1);
        act.setSettlementRaidInitialCount(a.getInt("settlement_raid_initial_count"));
        return act;
    }
    /** Repairs worlds saved after the ruin moved but before its encounter anchor followed it. */
    private static void repairRelocatedCrossingAnchor(WashedAshoreInstance act){
        BlockPos crossing=act.devilsCrossing();
        EncounterAnchor encounter=act.encounters().get(EncounterManager.THRASHER);
        if(crossing==null||encounter==null||encounter.status()==EncounterStatus.ACTIVE
                ||encounter.status()==EncounterStatus.COMPLETED||encounter.anchorPos().equals(crossing))return;
        CampaignCore.LOGGER.info("devils_crossing_anchor_repaired instance={} old={} new={}",
                act.actInstanceId(),encounter.anchorPos(),crossing);
        encounter.relocate(crossing,crossing.above());
    }
    /** Repairs the equivalent stale POI left by a relocated second-settlement placement. */
    private static void repairRelocatedOtherSettlementAnchor(WashedAshoreInstance act){
        BlockPos settlement=act.otherSettlement();
        EncounterAnchor encounter=act.encounters().get(EncounterManager.REGIONAL_C);
        if(settlement==null||encounter==null||encounter.status()==EncounterStatus.ACTIVE
                ||encounter.status()==EncounterStatus.COMPLETED||encounter.anchorPos().equals(settlement))return;
        CampaignCore.LOGGER.info("other_settlement_anchor_repaired instance={} old={} new={}",
                act.actInstanceId(),encounter.anchorPos(),settlement);
        encounter.relocate(settlement,settlement.above());
    }
    private static void putPos(CompoundTag tag,String key,BlockPos pos){if(pos!=null)tag.putLong(key,pos.asLong());}
    private static BlockPos readPos(CompoundTag tag,String key){return tag.contains(key,Tag.TAG_LONG)?BlockPos.of(tag.getLong(key)):null;}
    private static <E extends Enum<E>> E parseEnum(Class<E> type,String value,E fallback){try{return Enum.valueOf(type,value);}catch(Exception ignored){return fallback;}}
    private static void parseIds(String raw,Set<ResourceLocation> out){if(raw.isBlank())return;for(String s:raw.split(";")){ResourceLocation id=normalize(ResourceLocation.tryParse(s));if(id!=null)out.add(id);}}
    private static ResourceLocation normalize(ResourceLocation id){
        return id!=null&&id.getNamespace().equals(LegacyCampaignCompatibility.NAMESPACE)?CampaignCore.washedAshoreId(id.getPath()):id;
    }
}
