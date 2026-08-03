package dev.campaigncore.data;

import com.google.gson.*;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.act.RegionalQuestManager;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import dev.campaigncore.washedashore.act.WashedAshoreStage;
import dev.campaigncore.washedashore.encounter.ActivationCompletion;
import dev.campaigncore.washedashore.encounter.ActivationMilestone;
import dev.campaigncore.washedashore.encounter.ActivationProfile;
import dev.campaigncore.washedashore.encounter.EncounterCompletion;
import dev.campaigncore.washedashore.encounter.EncounterDefinition;
import dev.campaigncore.washedashore.encounter.EncounterManager;
import dev.campaigncore.washedashore.encounter.HordeProfile;
import dev.campaigncore.washedashore.encounter.SculkArenaProfile;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class EncounterDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> FIELDS=Set.of(
            "id","boss_entity","anchor","anchor_offset","activation_radius","reset_radius",
            "one_shot","required_stage","placeholder","retry_delay_ticks","reward_loot_table","activation","on_complete","raid","horde","sculk");
    private static final Set<String> RAID_MEMBER_FIELDS=Set.of("entity","count","role");
    private static final Set<String> HORDE_FIELDS=Set.of("spawn_radius","scan_radius","waves");
    private static final Set<String> WAVE_FIELDS=Set.of("members");
    private static final Set<String> SCULK_FIELDS=Set.of(
            "health_scale","damage_scale","mob_deaths_to_trigger","formation_radius","scan_radius",
            "wave_entity","wave_size","wave_count","wave_delay_ticks");
    private static final Set<String> COMPLETION_FIELDS=Set.of("message","advance_to","handlers");
    private static final Set<String> ACTIVATION_FIELDS=Set.of(
            "radius","fill_up","fill_down","threshold","nearby_prey","discovery_radius","biome_tag","milestones","on_full");
    private static final Set<String> MILESTONE_FIELDS=Set.of("mode","at","step","message","pass_level","effect","guard");
    private static final Set<String> ON_FULL_FIELDS=Set.of("message","guard","action");

    public EncounterDefinitionLoader(){super(GSON,"campaign_encounters");}

    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        Map<ResourceLocation,EncounterDefinition> parsed=new LinkedHashMap<>();
        List<String> errors=new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            try{
                EncounterDefinition definition=parse(GsonHelper.convertToJsonObject(entry.getValue(),"encounter"));
                if(parsed.putIfAbsent(definition.id(),definition)!=null)
                    throw new JsonParseException("duplicate encounter id "+definition.id());
            }catch(RuntimeException ex){errors.add(entry.getKey()+": "+ex.getMessage());}
        });
        if(!errors.isEmpty())throw new JsonParseException("Campaign encounter validation failed:\n - "+String.join("\n - ",errors));
        EncounterManager.definitions().replace(parsed);
        CampaignCore.LOGGER.info("campaign_encounters_reloaded count={} ids={}",parsed.size(),parsed.keySet());
    }

    private static EncounterDefinition parse(JsonObject json){
        for(String field:json.keySet())if(!FIELDS.contains(field))throw new JsonParseException("unknown field '"+field+"'");
        ResourceLocation id=resource(GsonHelper.getAsString(json,"id"),"id");
        ResourceLocation boss=resource(GsonHelper.getAsString(json,"boss_entity"),"boss_entity");
        String anchor=GsonHelper.getAsString(json,"anchor");
        if(!WashedAshoreInstance.SLOTS.contains(anchor))throw new JsonParseException("unknown anchor '"+anchor+"'; expected one of "+WashedAshoreInstance.SLOTS);
        Vec3i offset=offset(json);
        int activationRadius=GsonHelper.getAsInt(json,"activation_radius");
        int reset=GsonHelper.getAsInt(json,"reset_radius");
        boolean oneShot=GsonHelper.getAsBoolean(json,"one_shot",true);
        WashedAshoreStage stage=stage(GsonHelper.getAsString(json,"required_stage"));
        boolean placeholder=GsonHelper.getAsBoolean(json,"placeholder",false);
        int retryDelay=GsonHelper.getAsInt(json,"retry_delay_ticks",EncounterDefinition.DEFAULT_RETRY_DELAY_TICKS);
        ResourceLocation reward=json.has("reward_loot_table")
                ?resource(GsonHelper.getAsString(json,"reward_loot_table"),"reward_loot_table"):null;
        return new EncounterDefinition(id,boss,anchor,offset,activationRadius,reset,oneShot,stage,placeholder,retryDelay,reward,completion(json),activation(json),raid(json),horde(json),sculk(json));
    }

    private static SculkArenaProfile sculk(JsonObject json){
        if(!json.has("sculk"))return null;
        JsonObject obj=GsonHelper.convertToJsonObject(json.get("sculk"),"sculk");
        for(String field:obj.keySet())if(!SCULK_FIELDS.contains(field))throw new JsonParseException("unknown sculk field '"+field+"'");
        double healthScale=GsonHelper.getAsFloat(obj,"health_scale",0.5f);
        double damageScale=GsonHelper.getAsFloat(obj,"damage_scale",0.5f);
        int mobDeaths=GsonHelper.getAsInt(obj,"mob_deaths_to_trigger",10);
        int formationRadius=GsonHelper.getAsInt(obj,"formation_radius",20);
        int scanRadius=GsonHelper.getAsInt(obj,"scan_radius",40);
        ResourceLocation waveEntity=resource(GsonHelper.getAsString(obj,"wave_entity"),"wave_entity");
        int waveSize=GsonHelper.getAsInt(obj,"wave_size",5);
        int waveCount=GsonHelper.getAsInt(obj,"wave_count",3);
        int waveDelay=GsonHelper.getAsInt(obj,"wave_delay_ticks",400);
        return new SculkArenaProfile(healthScale,damageScale,mobDeaths,formationRadius,scanRadius,waveEntity,waveSize,waveCount,waveDelay);
    }

    private static HordeProfile horde(JsonObject json){
        if(!json.has("horde"))return null;
        JsonObject obj=GsonHelper.convertToJsonObject(json.get("horde"),"horde");
        for(String field:obj.keySet())if(!HORDE_FIELDS.contains(field))throw new JsonParseException("unknown horde field '"+field+"'");
        int spawnRadius=GsonHelper.getAsInt(obj,"spawn_radius",9);
        int scanRadius=GsonHelper.getAsInt(obj,"scan_radius",64);
        List<HordeProfile.Wave> waves=new ArrayList<>();
        for(JsonElement element:GsonHelper.getAsJsonArray(obj,"waves")){
            JsonObject wave=GsonHelper.convertToJsonObject(element,"wave");
            for(String field:wave.keySet())if(!WAVE_FIELDS.contains(field))throw new JsonParseException("unknown wave field '"+field+"'");
            waves.add(new HordeProfile.Wave(raidMembers(GsonHelper.getAsJsonArray(wave,"members"),"wave member")));
        }
        if(waves.isEmpty())throw new JsonParseException("horde must list at least one wave");
        return new HordeProfile(spawnRadius,scanRadius,waves);
    }

    private static List<EncounterDefinition.RaidMember> raid(JsonObject json){
        if(!json.has("raid"))return List.of();
        List<EncounterDefinition.RaidMember> members=raidMembers(GsonHelper.getAsJsonArray(json,"raid"),"raid member");
        if(members.isEmpty())throw new JsonParseException("raid must list at least one member");
        return members;
    }
    /** Parses a shared {@code [{entity,count}]} roster, used by both raids and horde waves. */
    private static List<EncounterDefinition.RaidMember> raidMembers(JsonArray array,String context){
        List<EncounterDefinition.RaidMember> members=new ArrayList<>();
        for(JsonElement element:array){
            JsonObject obj=GsonHelper.convertToJsonObject(element,context);
            for(String field:obj.keySet())if(!RAID_MEMBER_FIELDS.contains(field))throw new JsonParseException("unknown "+context+" field '"+field+"'");
            ResourceLocation entity=resource(GsonHelper.getAsString(obj,"entity"),"entity");
            int count=GsonHelper.getAsInt(obj,"count",1);
            dev.campaigncore.washedashore.encounter.RaidRole role=dev.campaigncore.washedashore.encounter.RaidRole.FRONTLINE;
            if(obj.has("role")){
                String name=GsonHelper.getAsString(obj,"role");
                role=dev.campaigncore.washedashore.encounter.RaidRole.CODEC.byName(name);
                if(role==null)throw new JsonParseException("unknown "+context+" role '"+name+"'");
            }
            members.add(new EncounterDefinition.RaidMember(entity,count,role));
        }
        return members;
    }
    private static ActivationProfile activation(JsonObject json){
        if(!json.has("activation"))return null;
        JsonObject obj=GsonHelper.convertToJsonObject(json.get("activation"),"activation");
        for(String field:obj.keySet())if(!ACTIVATION_FIELDS.contains(field))throw new JsonParseException("unknown activation field '"+field+"'");
        int radius=GsonHelper.getAsInt(obj,"radius");
        int fillUp=GsonHelper.getAsInt(obj,"fill_up",1);
        int fillDown=GsonHelper.getAsInt(obj,"fill_down",-1);
        int threshold=GsonHelper.getAsInt(obj,"threshold",100);
        int nearbyPrey=GsonHelper.getAsInt(obj,"nearby_prey",0);
        int discoveryRadius=GsonHelper.getAsInt(obj,"discovery_radius",0);
        Optional<ResourceLocation> biomeTag=obj.has("biome_tag")
                ?Optional.of(resource(GsonHelper.getAsString(obj,"biome_tag"),"biome_tag")):Optional.empty();
        List<ActivationMilestone> milestones=new ArrayList<>();
        if(obj.has("milestones"))for(JsonElement element:GsonHelper.getAsJsonArray(obj,"milestones"))
            milestones.add(milestone(GsonHelper.convertToJsonObject(element,"milestone")));
        ActivationCompletion onFull=obj.has("on_full")
                ?onFull(GsonHelper.convertToJsonObject(obj.get("on_full"),"on_full")):null;
        return new ActivationProfile(radius,fillUp,fillDown,threshold,nearbyPrey,discoveryRadius,biomeTag,milestones,onFull);
    }

    private static ActivationMilestone milestone(JsonObject json){
        for(String field:json.keySet())if(!MILESTONE_FIELDS.contains(field))throw new JsonParseException("unknown milestone field '"+field+"'");
        String mode=GsonHelper.getAsString(json,"mode");
        if(!RegionalQuestManager.milestoneModes().contains(mode))
            throw new JsonParseException("unknown milestone mode '"+mode+"'; expected one of "+RegionalQuestManager.milestoneModes());
        int at=GsonHelper.getAsInt(json,"at",0);
        int step=GsonHelper.getAsInt(json,"step",0);
        String message=GsonHelper.getAsString(json,"message",null);
        boolean passLevel=GsonHelper.getAsBoolean(json,"pass_level",false);
        String effect=optionalName(json,"effect",RegionalQuestManager.meterEffects());
        String guard=optionalName(json,"guard",RegionalQuestManager.meterGuards());
        return new ActivationMilestone(mode,at,step,message,passLevel,effect,guard);
    }

    private static ActivationCompletion onFull(JsonObject json){
        for(String field:json.keySet())if(!ON_FULL_FIELDS.contains(field))throw new JsonParseException("unknown on_full field '"+field+"'");
        String message=GsonHelper.getAsString(json,"message",null);
        String guard=optionalName(json,"guard",RegionalQuestManager.meterGuards());
        String action=GsonHelper.getAsString(json,"action");
        if(!RegionalQuestManager.meterActions().contains(action))
            throw new JsonParseException("unknown on_full action '"+action+"'; expected one of "+RegionalQuestManager.meterActions());
        return new ActivationCompletion(message,guard,action);
    }

    private static String optionalName(JsonObject json,String field,Set<String> allowed){
        if(!json.has(field))return null;
        String value=GsonHelper.getAsString(json,field);
        if(!allowed.contains(value))throw new JsonParseException("unknown "+field+" '"+value+"'; expected one of "+allowed);
        return value;
    }

    private static EncounterCompletion completion(JsonObject json){
        if(!json.has("on_complete"))return EncounterCompletion.DEFAULT;
        JsonObject obj=GsonHelper.convertToJsonObject(json.get("on_complete"),"on_complete");
        for(String field:obj.keySet())if(!COMPLETION_FIELDS.contains(field))throw new JsonParseException("unknown on_complete field '"+field+"'");
        String message=GsonHelper.getAsString(obj,"message","encounter_complete");
        List<WashedAshoreStage> advance=stages(obj);
        List<String> handlers=new ArrayList<>();
        if(obj.has("handlers"))for(JsonElement element:GsonHelper.getAsJsonArray(obj,"handlers")){
            String handler=element.getAsString();
            if(!EncounterManager.completionHandlers().contains(handler))
                throw new JsonParseException("unknown completion handler '"+handler+"'; expected one of "+EncounterManager.completionHandlers());
            handlers.add(handler);
        }
        return new EncounterCompletion(message,advance,handlers);
    }

    private static List<WashedAshoreStage> stages(JsonObject obj){
        if(!obj.has("advance_to"))return List.of();
        List<WashedAshoreStage> result=new ArrayList<>();
        for(JsonElement element:GsonHelper.getAsJsonArray(obj,"advance_to"))result.add(stage(element.getAsString()));
        return result;
    }

    private static Vec3i offset(JsonObject json){
        if(!json.has("anchor_offset"))return Vec3i.ZERO;
        JsonArray array=GsonHelper.getAsJsonArray(json,"anchor_offset");
        if(array.size()!=3)throw new JsonParseException("anchor_offset must have exactly 3 elements [x, y, z]");
        return new Vec3i(GsonHelper.convertToInt(array.get(0),"anchor_offset[0]"),
                GsonHelper.convertToInt(array.get(1),"anchor_offset[1]"),
                GsonHelper.convertToInt(array.get(2),"anchor_offset[2]"));
    }

    private static WashedAshoreStage stage(String value){
        try{return WashedAshoreStage.valueOf(value);}
        catch(IllegalArgumentException ex){throw new JsonParseException("unknown required_stage '"+value+"'");}
    }

    private static ResourceLocation resource(String value,String field){
        ResourceLocation parsed=ResourceLocation.tryParse(value);
        if(parsed==null)throw new JsonParseException("invalid resource location in '"+field+"': "+value);
        return parsed;
    }
}
