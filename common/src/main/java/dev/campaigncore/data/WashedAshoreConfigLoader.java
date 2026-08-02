package dev.campaigncore.data;

import com.google.gson.*;
import dev.campaigncore.CampaignCore;
import dev.campaigncore.washedashore.config.WashedAshoreConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;

/**
 * Loads act tuning (layout distances, recovery timings) from data/campaign_core/campaign_config.
 * Values missing from the file fall back to the code defaults on {@link WashedAshoreConfig}.
 */
public final class WashedAshoreConfigLoader extends SimpleJsonResourceReloadListener {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public WashedAshoreConfigLoader(){super(GSON,"campaign_config");}

    @Override protected void apply(Map<ResourceLocation,JsonElement> resources,ResourceManager manager,ProfilerFiller profiler){
        // Later namespaces override earlier ones for the same file name; apply in key order.
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry->{
            try{
                WashedAshoreConfig.load(GsonHelper.convertToJsonObject(entry.getValue(),"config"));
                CampaignCore.LOGGER.info("washed_ashore_config_loaded source={}",entry.getKey());
            }catch(RuntimeException ex){
                CampaignCore.LOGGER.error("washed_ashore_config_invalid source={}: {}",entry.getKey(),ex.getMessage());
            }
        });
    }
}
