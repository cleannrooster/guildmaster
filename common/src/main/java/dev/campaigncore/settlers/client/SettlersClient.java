package dev.campaigncore.settlers.client;

import dev.campaigncore.settlers.client.render.SettlerRenderer;
import dev.campaigncore.settlers.registry.SettlersEntities;
import dev.architectury.registry.client.level.entity.EntityRendererRegistry;

/// Common client setup, invoked from each loader's client entrypoint. Registers entity renderers;
/// loaders must only call this on the client dist.
public final class SettlersClient {
    private SettlersClient() {
    }

    public static void init() {
        EntityRendererRegistry.register(SettlersEntities.SETTLER, SettlerRenderer::new);
    }
}
