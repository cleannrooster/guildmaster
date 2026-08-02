package dev.campaigncore.washedashore.incident;

import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Persistent identity of one hub within one storyline layout instance. */
public record HubIncidentKey(UUID instanceId, ResourceLocation hubId) {
    public HubIncidentKey {
        if(instanceId==null||hubId==null)throw new IllegalArgumentException("incident hub key is incomplete");
    }
}
