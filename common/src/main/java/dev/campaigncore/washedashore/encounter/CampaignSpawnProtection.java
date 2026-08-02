package dev.campaigncore.washedashore.encounter;

import net.minecraft.world.entity.Entity;

/** Shared properties applied to entities created by Campaign Core encounters. */
public final class CampaignSpawnProtection {
    public static final String SUN_PROTECTED_TAG="campaign_core_sun_protected";

    private CampaignSpawnProtection(){}

    public static <T extends Entity> T protectFromSun(T entity){
        entity.addTag(SUN_PROTECTED_TAG);
        return entity;
    }

    public static boolean isProtectedFromSun(Entity entity){
        return entity.getTags().contains(SUN_PROTECTED_TAG);
    }
}
