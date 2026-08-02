package dev.campaigncore.data;

import com.google.gson.*;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.api.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class CampaignDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> FIELDS=Set.of(
            "version","initial_stage","stages","objectives","locations","encounters","hooks");

    public CampaignDefinitionLoader(){
        super(GSON,"campaigns");
    }

    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        Map<ResourceLocation,CampaignDefinition> parsed=new LinkedHashMap<>();
        List<String> errors=new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            try{
                CampaignDefinition definition=parse(entry.getKey(),GsonHelper.convertToJsonObject(entry.getValue(),"campaign"));
                if(parsed.putIfAbsent(definition.id(),definition)!=null)
                    throw new JsonParseException("duplicate campaign id "+definition.id());
            }catch(RuntimeException ex){
                errors.add(entry.getKey()+": "+ex.getMessage());
            }
        });
        if(!errors.isEmpty())throw new JsonParseException("Campaign definition validation failed:\n - "+String.join("\n - ",errors));
        CampaignApi.registry().replaceDataDefinitions(parsed);
        CampaignCore.LOGGER.info("campaign_definitions_reloaded count={} ids={}",parsed.size(),parsed.keySet());
    }

    static CampaignDefinition parse(ResourceLocation id,JsonObject json){
        for(String field:json.keySet())if(!FIELDS.contains(field))
            throw new JsonParseException("unknown field '"+field+"'");
        int version=GsonHelper.getAsInt(json,"version");
        if(version<1)throw new JsonParseException("version must be at least 1");
        Set<ResourceLocation> stages=ids(json,"stages",id);
        if(stages.isEmpty())throw new JsonParseException("stages must not be empty");
        ResourceLocation initial=id(GsonHelper.getAsString(json,"initial_stage"),id);
        if(!stages.contains(initial))throw new JsonParseException("initial_stage "+initial+" is not declared in stages");
        return new CampaignDefinition(id,version,initial,stages,
                ids(json,"objectives",id),ids(json,"locations",id),
                ids(json,"encounters",id),optionalIds(json,"hooks",id));
    }

    private static Set<ResourceLocation> ids(JsonObject json,String field,ResourceLocation campaign){
        if(!json.has(field))throw new JsonParseException("missing required array '"+field+"'");
        JsonArray array=GsonHelper.getAsJsonArray(json,field);
        LinkedHashSet<ResourceLocation> result=new LinkedHashSet<>();
        for(JsonElement element:array){
            if(!element.isJsonPrimitive()||!element.getAsJsonPrimitive().isString())
                throw new JsonParseException(field+" entries must be strings");
            ResourceLocation value=id(element.getAsString(),campaign);
            if(!result.add(value))throw new JsonParseException("duplicate "+field+" id "+value);
        }
        return result;
    }

    private static Set<ResourceLocation> optionalIds(JsonObject json,String field,ResourceLocation campaign){
        return json.has(field)?ids(json,field,campaign):Set.of();
    }

    private static ResourceLocation id(String value,ResourceLocation campaign){
        ResourceLocation parsed=value.indexOf(':')>=0?ResourceLocation.tryParse(value):
                ResourceLocation.tryBuild(campaign.getNamespace(),campaign.getPath()+"/"+value);
        if(parsed==null)throw new JsonParseException("invalid resource location '"+value+"'");
        return parsed;
    }
}
