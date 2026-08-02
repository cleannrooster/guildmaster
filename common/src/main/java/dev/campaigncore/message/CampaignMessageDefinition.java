package dev.campaigncore.message;

import net.minecraft.resources.ResourceLocation;

public record CampaignMessageDefinition(
        ResourceLocation id,
        CampaignMessageChannel channel,
        String translation
) {
    public CampaignMessageDefinition {
        if(id==null||channel==null)throw new IllegalArgumentException("message id and channel are required");
        if(translation==null||translation.isBlank())throw new IllegalArgumentException("translation cannot be blank");
    }
}
