package dev.campaigncore.data;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.campaign.*;
import dev.campaigncore.compat.LegacyCampaignCompatibility;
import dev.campaigncore.washedashore.act.*;
import dev.campaigncore.washedashore.data.WashedAshoreSavedData;
import dev.campaigncore.washedashore.encounter.EncounterAnchor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

public final class CampaignSavedData extends SavedData {
    public static final int DATA_VERSION = 1;
    private static final String DATA_NAME = "campaign_core_campaigns";
    private final Map<ResourceLocation,CampaignInstance> campaigns=new LinkedHashMap<>();
    private final Map<UUID,Map<ResourceLocation,PlayerCampaignProgress>> players=new HashMap<>();
    private int dataVersion=DATA_VERSION;
    private int legacyMigrationVersion;

    public static CampaignSavedData get(ServerLevel level){
        ServerLevel overworld=level.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CampaignSavedData::new,CampaignSavedData::load,null),DATA_NAME);
    }
    public Map<ResourceLocation,CampaignInstance> campaigns(){return Collections.unmodifiableMap(campaigns);}
    public CampaignInstance campaign(ResourceLocation id){return campaigns.get(id);}
    public PlayerCampaignProgress player(UUID player,ResourceLocation campaign){
        return players.computeIfAbsent(player,k->new HashMap<>()).computeIfAbsent(campaign,k->new PlayerCampaignProgress());
    }

    public void migrateLegacy(ServerLevel level){
        if(legacyMigrationVersion>=DATA_VERSION)return;
        WashedAshoreSavedData legacy=WashedAshoreSavedData.get(level);
        WashedAshoreInstance old=legacy.act();
        CampaignInstance instance=new CampaignInstance(CampaignCore.WASHED_ASHORE,1);
        instance.restoreIdentity(old.actInstanceId(),Math.max(1,old.layoutVersion()),mapStatus(old.generationStatus()));
        ResourceLocation dimension=level.dimension().location();
        copyLocation(instance,dimension,"beach",old.beachSpawn());
        copyLocation(instance,dimension,"guide",old.guideLandmark());
        copyLocation(instance,dimension,"graveyard",old.undertakerGraveyard());
        copyLocation(instance,dimension,"settlement",old.settlement());
        copyLocation(instance,dimension,"dark_forest",old.darkForest());
        copyLocation(instance,dimension,"devils_crossing",old.devilsCrossing());
        copyLocation(instance,dimension,"other_settlement",old.otherSettlement());
        copyLocation(instance,dimension,"raven",old.ravenArena());
        for(Map.Entry<ResourceLocation,EncounterAnchor> entry:old.encounters().entrySet()){
            EncounterAnchor source=entry.getValue();EncounterState target=new EncounterState();
            target.restore(source.status().name(),source.activeBossUuid(),source.anchorPos(),source.bossSpawnPos(),source.oneShot());
            instance.encounters().put(remap("encounter",entry.getKey()),target);
        }
        old.completedWorldObjectives().forEach(id->instance.completedWorldObjectives().add(remap("objective",id)));
        campaigns.put(CampaignCore.WASHED_ASHORE,instance);
        legacy.playersView().forEach((uuid,progress)->players
                .computeIfAbsent(uuid,k->new HashMap<>())
                .put(CampaignCore.WASHED_ASHORE,migratePlayer(progress)));
        legacyMigrationVersion=DATA_VERSION;setDirty();
        CampaignCore.LOGGER.info("legacy_migration_complete source=campaign_core_washed_ashore_act_one campaign={} instance={} locations={} encounters={} world_objectives={} players={}",
                CampaignCore.WASHED_ASHORE,instance.instanceId(),instance.locations().size(),instance.encounters().size(),
                instance.completedWorldObjectives().size(),legacy.playersView().size());
    }

    public void mirrorStage(UUID player,WashedAshoreStage stage){
        PlayerCampaignProgress progress=player(player,CampaignCore.WASHED_ASHORE);
        progress.setCurrentStage(stageId(stage));setDirty();
    }

    private static PlayerCampaignProgress migratePlayer(WashedAshoreProgress old){
        PlayerCampaignProgress result=new PlayerCampaignProgress();result.setCurrentStage(stageId(old.stage()));
        old.defeatedBosses().forEach(id->result.defeatedEncounters().add(remap("encounter",id)));
        old.discoveredLandmarks().forEach(id->result.discoveredLocations().add(remap("location",id)));
        if(old.dreadQuest()!=RegionalQuestStage.LOCKED)result.activeObjectives().add(id("objective/dread"));
        if(old.crossingQuest()!=RegionalQuestStage.LOCKED)result.activeObjectives().add(id("objective/devils_crossing"));
        if(old.dreadQuest()==RegionalQuestStage.COMPLETE){result.activeObjectives().remove(id("objective/dread"));result.completedObjectives().add(id("objective/dread"));}
        if(old.crossingQuest()==RegionalQuestStage.COMPLETE){result.activeObjectives().remove(id("objective/devils_crossing"));result.completedObjectives().add(id("objective/devils_crossing"));}
        result.variables().put(id("variable/intro_played"),CampaignValue.of(old.introPlayed()));
        result.variables().put(id("variable/dread_quest"),CampaignValue.of(old.dreadQuest().name()));
        result.variables().put(id("variable/crossing_quest"),CampaignValue.of(old.crossingQuest().name()));
        result.variables().put(id("variable/dread_level"),CampaignValue.of(old.dreadLevel()));
        result.variables().put(id("variable/dread_manifest_ticks"),CampaignValue.of(old.dreadManifestTicks()));
        result.variables().put(id("variable/crossing_investigation"),CampaignValue.of(old.crossingInvestigation()));
        result.variables().put(id("variable/fragment_dread_invocation"),CampaignValue.of(old.fragmentDreadInvocation()));
        result.variables().put(id("variable/fragment_crossing_invocation"),CampaignValue.of(old.fragmentCrossingInvocation()));
        return result;
    }
    private static void copyLocation(CampaignInstance instance,ResourceLocation dimension,String path,BlockPos pos){
        if(pos==null)return;ResourceLocation id=id("location/"+path);instance.locations().put(id,new ResolvedLocation(id,dimension,pos));
    }
    private static ResourceLocation stageId(WashedAshoreStage stage){return id("stage/"+stage.name().toLowerCase(Locale.ROOT));}
    private static ResourceLocation id(String path){return CampaignCore.id("washed_ashore/"+path);}
    private static ResourceLocation remap(String kind,ResourceLocation old){
        if(old.getNamespace().equals(LegacyCampaignCompatibility.NAMESPACE))return id(kind+"/"+old.getPath());
        return old;
    }
    private static CampaignGenerationStatus mapStatus(WashedAshoreGenerationStatus status){
        try{return CampaignGenerationStatus.valueOf(status.name());}catch(IllegalArgumentException ignored){return CampaignGenerationStatus.DEGRADED;}
    }

    private static CampaignSavedData load(CompoundTag tag,HolderLookup.Provider registries){
        CampaignSavedData data=new CampaignSavedData();data.dataVersion=Math.max(1,tag.getInt("data_version"));
        data.legacyMigrationVersion=tag.getInt("legacy_migration_version");
        CompoundTag campaigns=tag.getCompound("campaigns");for(String key:campaigns.getAllKeys())try{
            CampaignInstance instance=CampaignInstance.load(campaigns.getCompound(key));data.campaigns.put(instance.campaignId(),instance);
        }catch(RuntimeException ex){CampaignCore.LOGGER.error("campaign_load_failed id={}",key,ex);}
        CompoundTag players=tag.getCompound("players");for(String uuidKey:players.getAllKeys())try{
            UUID uuid=UUID.fromString(uuidKey);CompoundTag byCampaign=players.getCompound(uuidKey);Map<ResourceLocation,PlayerCampaignProgress> values=new HashMap<>();
            for(String campaignKey:byCampaign.getAllKeys()){ResourceLocation id=ResourceLocation.tryParse(campaignKey);if(id!=null)values.put(id,PlayerCampaignProgress.load(byCampaign.getCompound(campaignKey)));}
            data.players.put(uuid,values);
        }catch(IllegalArgumentException ignored){}
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries){
        tag.putInt("data_version",dataVersion);tag.putInt("legacy_migration_version",legacyMigrationVersion);
        CompoundTag campaignTag=new CompoundTag();campaigns.forEach((id,value)->campaignTag.put(id.toString(),value.save()));tag.put("campaigns",campaignTag);
        CompoundTag playerTag=new CompoundTag();players.forEach((uuid,byCampaign)->{CompoundTag values=new CompoundTag();byCampaign.forEach((id,value)->values.put(id.toString(),value.save()));playerTag.put(uuid.toString(),values);});tag.put("players",playerTag);
        return tag;
    }
}
