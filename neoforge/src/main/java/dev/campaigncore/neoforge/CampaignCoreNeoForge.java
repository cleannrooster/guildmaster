package dev.campaigncore.neoforge;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.client.CampaignCoreClient;
import dev.campaigncore.settlers.client.SettlersClient;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.neoforge.NeoForgeConfigScreen;
import dev.campaigncore.settlers.registry.SettlersEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(CampaignCore.MOD_ID)
public final class CampaignCoreNeoForge {
    public CampaignCoreNeoForge(IEventBus modEventBus, ModContainer container) {
        CampaignCore.init();

        // Settler attributes are loader-specific. CampaignCore.init() already ran SettlersMod.init(),
        // so the entity type is bound by the time this event fires.
        modEventBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(SettlersEntities.SETTLER.get(), SettlerEntity.createAttributes().build()));

        if (FMLEnvironment.dist == Dist.CLIENT) {
            CampaignCoreClient.init();
            SettlersClient.init();
            NeoForgeConfigScreen.register(container);
        }
    }
}
