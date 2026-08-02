package dev.campaigncore.message;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class CampaignMessageRegistry {
    private volatile Map<ResourceLocation,CampaignMessageDefinition> definitions=Map.of();
    public Optional<CampaignMessageDefinition> get(ResourceLocation id){return Optional.ofNullable(definitions.get(id));}
    public Collection<CampaignMessageDefinition> definitions(){return List.copyOf(definitions.values());}
    public synchronized void replace(Map<ResourceLocation,CampaignMessageDefinition> values){definitions=Map.copyOf(values);}
}
