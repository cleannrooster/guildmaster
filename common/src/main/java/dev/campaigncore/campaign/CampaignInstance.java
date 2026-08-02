package dev.campaigncore.campaign;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class CampaignInstance {
    private UUID instanceId=UUID.randomUUID();
    private final ResourceLocation campaignId;
    private int definitionVersion;
    private CampaignGenerationStatus generationStatus=CampaignGenerationStatus.UNINITIALIZED;
    private final Map<ResourceLocation,ResolvedLocation> locations=new LinkedHashMap<>();
    private final Map<ResourceLocation,EncounterState> encounters=new LinkedHashMap<>();
    private final Set<ResourceLocation> completedWorldObjectives=new HashSet<>();
    private final Map<ResourceLocation,CampaignValue> variables=new HashMap<>();

    public CampaignInstance(ResourceLocation campaignId,int definitionVersion){this.campaignId=campaignId;this.definitionVersion=definitionVersion;}
    public UUID instanceId(){return instanceId;} public ResourceLocation campaignId(){return campaignId;}
    public int definitionVersion(){return definitionVersion;} public CampaignGenerationStatus generationStatus(){return generationStatus;}
    public Map<ResourceLocation,ResolvedLocation> locations(){return locations;} public Map<ResourceLocation,EncounterState> encounters(){return encounters;}
    public Set<ResourceLocation> completedWorldObjectives(){return completedWorldObjectives;} public Map<ResourceLocation,CampaignValue> variables(){return variables;}
    public void restoreIdentity(UUID id,int version,CampaignGenerationStatus status){instanceId=id;definitionVersion=version;generationStatus=status;}
    public CompoundTag save(){
        CompoundTag tag=new CompoundTag();tag.putUUID("instance_id",instanceId);tag.putString("campaign_id",campaignId.toString());
        tag.putInt("definition_version",definitionVersion);tag.putString("generation_status",generationStatus.name());
        CompoundTag locs=new CompoundTag();locations.forEach((id,v)->locs.put(id.toString(),v.save()));tag.put("locations",locs);
        CompoundTag encs=new CompoundTag();encounters.forEach((id,v)->encs.put(id.toString(),v.save()));tag.put("encounters",encs);
        tag.putString("completed_world_objectives",completedWorldObjectives.stream().map(ResourceLocation::toString).sorted().reduce("",(a,b)->a+(a.isEmpty()?"":";")+b));
        CompoundTag vars=new CompoundTag();variables.forEach((id,v)->vars.put(id.toString(),v.save()));tag.put("variables",vars);return tag;
    }
    public static CampaignInstance load(CompoundTag tag){
        ResourceLocation id=ResourceLocation.parse(tag.getString("campaign_id"));CampaignInstance result=new CampaignInstance(id,tag.getInt("definition_version"));
        result.restoreIdentity(tag.getUUID("instance_id"),tag.getInt("definition_version"),CampaignGenerationStatus.valueOf(tag.getString("generation_status")));
        CompoundTag locs=tag.getCompound("locations");for(String key:locs.getAllKeys()){ResourceLocation k=ResourceLocation.tryParse(key);if(k!=null)result.locations.put(k,ResolvedLocation.load(locs.getCompound(key)));}
        CompoundTag encs=tag.getCompound("encounters");for(String key:encs.getAllKeys()){ResourceLocation k=ResourceLocation.tryParse(key);if(k!=null)result.encounters.put(k,EncounterState.load(encs.getCompound(key)));}
        String raw=tag.getString("completed_world_objectives");if(!raw.isBlank())for(String s:raw.split(";")){ResourceLocation k=ResourceLocation.tryParse(s);if(k!=null)result.completedWorldObjectives.add(k);}
        CompoundTag vars=tag.getCompound("variables");for(String key:vars.getAllKeys()){ResourceLocation k=ResourceLocation.tryParse(key);if(k!=null)result.variables.put(k,CampaignValue.load(vars.getCompound(key)));}
        return result;
    }
}
