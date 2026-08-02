package dev.campaigncore.data;

import com.google.gson.*;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.act.LocationTrigger;
import dev.campaigncore.washedashore.act.WashedAshoreInstance;
import dev.campaigncore.washedashore.act.WashedAshoreManager;
import dev.campaigncore.washedashore.act.WashedAshoreStage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class LocationTriggerLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> FIELDS=Set.of(
            "location","radius","below_stage","min_stage","condition","discover","advance_to","message","handler");

    public LocationTriggerLoader(){super(GSON,"campaign_location_triggers");}

    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        List<LocationTrigger> parsed=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            try{
                JsonObject root=GsonHelper.convertToJsonObject(entry.getValue(),"location triggers");
                JsonArray triggers=GsonHelper.getAsJsonArray(root,"triggers");
                for(JsonElement element:triggers)parsed.add(parse(GsonHelper.convertToJsonObject(element,"trigger")));
            }catch(RuntimeException ex){errors.add(entry.getKey()+": "+ex.getMessage());}
        });
        if(!errors.isEmpty())throw new JsonParseException("Location trigger validation failed:\n - "+String.join("\n - ",errors));
        WashedAshoreManager.locationTriggers().replace(parsed);
        CampaignCore.LOGGER.info("campaign_location_triggers_reloaded count={}",parsed.size());
    }

    private static LocationTrigger parse(JsonObject json){
        for(String field:json.keySet())if(!FIELDS.contains(field))throw new JsonParseException("unknown field '"+field+"'");
        String location=GsonHelper.getAsString(json,"location");
        if(!WashedAshoreInstance.SLOTS.contains(location))throw new JsonParseException("unknown location slot '"+location+"'; expected one of "+WashedAshoreInstance.SLOTS);
        int radius=GsonHelper.getAsInt(json,"radius");
        if(radius<=0)throw new JsonParseException("radius must be positive");
        Optional<WashedAshoreStage> belowStage=optionalStage(json,"below_stage");
        Optional<WashedAshoreStage> minStage=optionalStage(json,"min_stage");
        Optional<String> condition=optionalString(json,"condition");
        if(condition.isPresent()&&!WashedAshoreManager.triggerConditions().contains(condition.get()))
            throw new JsonParseException("unknown condition '"+condition.get()+"'; expected one of "+WashedAshoreManager.triggerConditions());
        Optional<ResourceLocation> discover=optionalResource(json,"discover");
        List<WashedAshoreStage> advance=stages(json);
        Optional<String> message=optionalString(json,"message");
        Optional<String> handler=optionalString(json,"handler");
        if(handler.isPresent()&&!WashedAshoreManager.triggerHandlers().contains(handler.get()))
            throw new JsonParseException("unknown handler '"+handler.get()+"'; expected one of "+WashedAshoreManager.triggerHandlers());
        return new LocationTrigger(location,radius,belowStage,minStage,condition,discover,advance,message,handler);
    }

    private static List<WashedAshoreStage> stages(JsonObject json){
        if(!json.has("advance_to"))return List.of();
        List<WashedAshoreStage> result=new ArrayList<>();
        for(JsonElement element:GsonHelper.getAsJsonArray(json,"advance_to"))result.add(stage(element.getAsString()));
        return result;
    }
    private static Optional<WashedAshoreStage> optionalStage(JsonObject json,String field){
        return json.has(field)?Optional.of(stage(GsonHelper.getAsString(json,field))):Optional.empty();
    }
    private static WashedAshoreStage stage(String value){
        try{return WashedAshoreStage.valueOf(value);}
        catch(IllegalArgumentException ex){throw new JsonParseException("unknown stage '"+value+"'");}
    }
    private static Optional<String> optionalString(JsonObject json,String field){
        return json.has(field)?Optional.of(GsonHelper.getAsString(json,field)):Optional.empty();
    }
    private static Optional<ResourceLocation> optionalResource(JsonObject json,String field){
        if(!json.has(field))return Optional.empty();
        String raw=GsonHelper.getAsString(json,field);
        ResourceLocation parsed=ResourceLocation.tryParse(raw);
        if(parsed==null)throw new JsonParseException("invalid resource location in '"+field+"': "+raw);
        return Optional.of(parsed);
    }
}
