package dev.campaigncore.api;

import net.minecraft.resources.ResourceLocation;
import java.util.Set;

public record CampaignDefinition(
        ResourceLocation id,
        int definitionVersion,
        ResourceLocation initialStage,
        Set<ResourceLocation> stages,
        Set<ResourceLocation> objectives,
        Set<ResourceLocation> locations,
        Set<ResourceLocation> encounters,
        Set<ResourceLocation> hooks
) {
    public CampaignDefinition {
        if (id == null) throw new IllegalArgumentException("id cannot be null");
        if (definitionVersion < 1) throw new IllegalArgumentException("definitionVersion must be positive");
        stages=Set.copyOf(stages);
        objectives=Set.copyOf(objectives);
        locations=Set.copyOf(locations);
        encounters=Set.copyOf(encounters);
        hooks=Set.copyOf(hooks);
        if(initialStage!=null&&!stages.contains(initialStage))
            throw new IllegalArgumentException("initialStage must be declared in stages");
    }

    public CampaignDefinition(ResourceLocation id,int definitionVersion){
        this(id,definitionVersion,null,Set.of(),Set.of(),Set.of(),Set.of(),Set.of());
    }
}
