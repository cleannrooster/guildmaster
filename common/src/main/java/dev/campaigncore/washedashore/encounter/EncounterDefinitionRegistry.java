package dev.campaigncore.washedashore.encounter;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Holds the datapack-loaded encounter table; swapped atomically on resource reload. */
public final class EncounterDefinitionRegistry {
    private volatile Map<ResourceLocation,EncounterDefinition> definitions=Map.of();
    public Optional<EncounterDefinition> get(ResourceLocation id){return Optional.ofNullable(definitions.get(id));}
    public Collection<EncounterDefinition> all(){return List.copyOf(definitions.values());}
    public synchronized void replace(Map<ResourceLocation,EncounterDefinition> values){definitions=Map.copyOf(values);}
}
