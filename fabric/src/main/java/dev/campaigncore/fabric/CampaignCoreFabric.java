package dev.campaigncore.fabric;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.registry.SettlersEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

public final class CampaignCoreFabric implements ModInitializer {
    @Override public void onInitialize() {
        CampaignCore.init();

        // Settler attributes are loader-specific. CampaignCore.init() already ran SettlersMod.init(),
        // so ENTITY_TYPES are registered (Architectury registers eagerly on Fabric) and .get() is valid.
        FabricDefaultAttributeRegistry.register(SettlersEntities.SETTLER.get(), SettlerEntity.createAttributes());
    }
}
