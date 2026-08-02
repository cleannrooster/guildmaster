package dev.campaigncore.api;

public final class CampaignApi {
    private static final CampaignRegistry REGISTRY = new CampaignRegistry();
    private CampaignApi() {}
    public static CampaignRegistry registry() { return REGISTRY; }
}
