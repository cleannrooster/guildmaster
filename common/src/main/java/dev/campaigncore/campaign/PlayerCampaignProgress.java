package dev.campaigncore.campaign;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class PlayerCampaignProgress {
    private ResourceLocation currentStage;
    private final Set<ResourceLocation> activeObjectives=new HashSet<>();
    private final Set<ResourceLocation> completedObjectives=new HashSet<>();
    private final Set<ResourceLocation> discoveredLocations=new HashSet<>();
    private final Set<ResourceLocation> defeatedEncounters=new HashSet<>();
    private final Map<ResourceLocation,CampaignValue> variables=new HashMap<>();

    public ResourceLocation currentStage(){return currentStage;}
    public Set<ResourceLocation> activeObjectives(){return activeObjectives;}
    public Set<ResourceLocation> completedObjectives(){return completedObjectives;}
    public Set<ResourceLocation> discoveredLocations(){return discoveredLocations;}
    public Set<ResourceLocation> defeatedEncounters(){return defeatedEncounters;}
    public Map<ResourceLocation,CampaignValue> variables(){return variables;}
    public void setCurrentStage(ResourceLocation stage){currentStage=stage;}

    public CompoundTag save(){
        CompoundTag tag=new CompoundTag(); if(currentStage!=null)tag.putString("current_stage",currentStage.toString());
        putIds(tag,"active_objectives",activeObjectives); putIds(tag,"completed_objectives",completedObjectives);
        putIds(tag,"discovered_locations",discoveredLocations); putIds(tag,"defeated_encounters",defeatedEncounters);
        CompoundTag vars=new CompoundTag();variables.forEach((id,value)->vars.put(id.toString(),value.save()));tag.put("variables",vars);return tag;
    }
    public static PlayerCampaignProgress load(CompoundTag tag){
        PlayerCampaignProgress result=new PlayerCampaignProgress();
        if(tag.contains("current_stage"))result.currentStage=ResourceLocation.tryParse(tag.getString("current_stage"));
        readIds(tag.getString("active_objectives"),result.activeObjectives);readIds(tag.getString("completed_objectives"),result.completedObjectives);
        readIds(tag.getString("discovered_locations"),result.discoveredLocations);readIds(tag.getString("defeated_encounters"),result.defeatedEncounters);
        CompoundTag vars=tag.getCompound("variables");for(String key:vars.getAllKeys()){ResourceLocation id=ResourceLocation.tryParse(key);if(id!=null)try{result.variables.put(id,CampaignValue.load(vars.getCompound(key)));}catch(RuntimeException ignored){}}
        return result;
    }
    private static void putIds(CompoundTag tag,String key,Set<ResourceLocation> ids){tag.putString(key,ids.stream().map(ResourceLocation::toString).sorted().reduce("",(a,b)->a+(a.isEmpty()?"":";")+b));}
    private static void readIds(String raw,Set<ResourceLocation> ids){if(raw.isBlank())return;for(String s:raw.split(";")){ResourceLocation id=ResourceLocation.tryParse(s);if(id!=null)ids.add(id);}}
}
