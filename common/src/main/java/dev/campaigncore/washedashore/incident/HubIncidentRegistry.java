package dev.campaigncore.washedashore.incident;

import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class HubIncidentRegistry {
    private static volatile Map<ResourceLocation,HubDefinition> hubs=Map.of();
    private static volatile Map<ResourceLocation,HubIncidentDefinition> incidents=Map.of();
    private HubIncidentRegistry(){}
    public static Map<ResourceLocation,HubDefinition> hubs(){return hubs;}
    public static Optional<HubIncidentDefinition> incident(ResourceLocation id){return Optional.ofNullable(incidents.get(id));}
    public static Map<ResourceLocation,HubIncidentDefinition> incidents(){return incidents;}
    public static void loadHubs(Map<ResourceLocation,HubDefinition> values){hubs=Map.copyOf(values);}
    public static void loadIncidents(Map<ResourceLocation,HubIncidentDefinition> values){incidents=Map.copyOf(values);}
}
