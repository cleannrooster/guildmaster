package dev.campaigncore.fabric;

import dev.campaigncore.client.CampaignCoreClient;
import dev.campaigncore.settlers.client.SettlersClient;
import net.fabricmc.api.ClientModInitializer;

public final class CampaignCoreFabricClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        CampaignCoreClient.init();
        SettlersClient.init();
    }
}
