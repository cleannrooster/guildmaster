package dev.campaigncore.data;

import com.google.gson.*;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.message.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

public final class CampaignMessageDefinitionLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Set<String> FIELDS=Set.of("channel","translation");
    public CampaignMessageDefinitionLoader(){super(GSON,"campaign_messages");}

    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        Map<ResourceLocation,CampaignMessageDefinition> parsed=new LinkedHashMap<>();
        List<String> errors=new ArrayList<>();
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(bundle->{
            try{
                JsonObject root=GsonHelper.convertToJsonObject(bundle.getValue(),"message bundle");
                for(Map.Entry<String,JsonElement> entry:root.entrySet()){
                    ResourceLocation id=ResourceLocation.tryBuild(bundle.getKey().getNamespace(),bundle.getKey().getPath()+"/"+entry.getKey());
                    if(id==null)throw new JsonParseException("invalid local message id '"+entry.getKey()+"'");
                    CampaignMessageDefinition definition=parse(id,GsonHelper.convertToJsonObject(entry.getValue(),"message"));
                    if(parsed.putIfAbsent(id,definition)!=null)throw new JsonParseException("duplicate message id "+id);
                }
            }catch(RuntimeException ex){errors.add(bundle.getKey()+": "+ex.getMessage());}
        });
        if(!errors.isEmpty())throw new JsonParseException("Campaign message validation failed:\n - "+String.join("\n - ",errors));
        CampaignMessageManager.registry().replace(parsed);
        CampaignCore.LOGGER.info("campaign_messages_reloaded count={}",parsed.size());
    }

    private static CampaignMessageDefinition parse(ResourceLocation id,JsonObject json){
        for(String field:json.keySet())if(!FIELDS.contains(field))throw new JsonParseException("unknown field '"+field+"' in "+id);
        CampaignMessageChannel channel;
        try{channel=CampaignMessageChannel.parse(GsonHelper.getAsString(json,"channel"));}
        catch(IllegalArgumentException ex){throw new JsonParseException(ex.getMessage());}
        return new CampaignMessageDefinition(id,channel,GsonHelper.getAsString(json,"translation"));
    }
}
