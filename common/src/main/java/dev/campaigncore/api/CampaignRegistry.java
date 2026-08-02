package dev.campaigncore.api;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class CampaignRegistry {
    private final Map<ResourceLocation, CampaignDefinition> builtins = new LinkedHashMap<>();
    private volatile Map<ResourceLocation, CampaignDefinition> dataDefinitions = Map.of();

    public synchronized CampaignDefinition register(ResourceLocation id, int version) {
        CampaignDefinition definition = new CampaignDefinition(id, version);
        if (builtins.putIfAbsent(id, definition) != null)
            throw new IllegalStateException("Duplicate campaign definition " + id);
        return definition;
    }

    public Optional<CampaignDefinition> get(ResourceLocation id) {
        CampaignDefinition data=dataDefinitions.get(id);
        return Optional.ofNullable(data!=null?data:builtins.get(id));
    }

    public Collection<CampaignDefinition> definitions() {
        Map<ResourceLocation,CampaignDefinition> merged=new LinkedHashMap<>(builtins);
        merged.putAll(dataDefinitions);
        return List.copyOf(merged.values());
    }

    public synchronized void replaceDataDefinitions(Map<ResourceLocation,CampaignDefinition> definitions){
        dataDefinitions=Map.copyOf(definitions);
    }

    public int dataDefinitionCount(){
        return dataDefinitions.size();
    }
}
