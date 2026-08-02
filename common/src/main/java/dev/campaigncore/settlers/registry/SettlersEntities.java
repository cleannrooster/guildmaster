package dev.campaigncore.settlers.registry;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class SettlersEntities {
    // The DeferredRegister mod id selects the NeoForge mod event bus to register on, so it must be a
    // real loaded mod — after the merge that is campaign_core, not "settlers" (which no longer has a
    // ModContainer). Content ids keep the preserved settlers: namespace via explicit ResourceLocations.
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(CampaignCore.MOD_ID, Registries.ENTITY_TYPE);

    // MISC category: Settlers are managed by their settlement (spawned from the roster, persistence
    // required), never by the natural mob-cap spawner.
    public static final RegistrySupplier<EntityType<SettlerEntity>> SETTLER =
            ENTITY_TYPES.register(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "settler"),
                    () -> EntityType.Builder.of(SettlerEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(10)
                    // 1.21.1: EntityType.Builder.build takes the registry-name String, not a ResourceKey.
                    .build("settler"));

    private SettlersEntities() {
    }
}
