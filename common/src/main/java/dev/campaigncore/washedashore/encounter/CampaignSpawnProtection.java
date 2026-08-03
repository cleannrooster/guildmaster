package dev.campaigncore.washedashore.encounter;

import dev.campaigncore.CampaignCore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

/** Shared properties applied to entities created by Campaign Core encounters. */
public final class CampaignSpawnProtection {
    public static final String SUN_PROTECTED_TAG="campaign_core_sun_protected";

    private CampaignSpawnProtection(){}

    public static <T extends Entity> T protectFromSun(T entity){
        entity.addTag(SUN_PROTECTED_TAG);
        preventRpgMinibossRecruitment(entity);
        return entity;
    }

    /** Marks only Campaign Core-spawned RPG Minibosses as non-hireable. */
    public static <T extends Entity> T preventRpgMinibossRecruitment(T entity){
        var id=BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if(id==null||!id.getNamespace().equals("rpg-minibosses"))return entity;
        try{
            // Optional dependency: MinibossEntity#setCantHire updates its synced flag, which the
            // RPG Minibosses save code persists under the boolean NBT key "cantHire".
            entity.getClass().getMethod("setCantHire",boolean.class).invoke(entity,true);
        }catch(ReflectiveOperationException ex){
            CampaignCore.LOGGER.warn("rpg_miniboss_cant_hire_failed entity={} class={}",id,entity.getClass().getName(),ex);
        }
        return entity;
    }

    public static boolean isProtectedFromSun(Entity entity){
        return entity.getTags().contains(SUN_PROTECTED_TAG);
    }
}
