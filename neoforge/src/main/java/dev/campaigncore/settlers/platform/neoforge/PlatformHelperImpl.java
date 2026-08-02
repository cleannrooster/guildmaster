package dev.campaigncore.settlers.platform.neoforge;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.function.Supplier;

/// NeoForge implementation of {@link dev.campaigncore.settlers.platform.PlatformHelper}. Architectury's
/// @ExpectPlatform links to this class by the `<package>.neoforge.<Class>Impl` convention.
/// {@code DeferredSpawnEggItem} resolves the entity type lazily, so it is safe even if the item is built
/// before the entity type is bound.
public final class PlatformHelperImpl {
    private PlatformHelperImpl() {
    }

    public static Item createSpawnEgg(Supplier<? extends EntityType<? extends Mob>> type, int background, int highlight) {
        return new DeferredSpawnEggItem(type, background, highlight, new Item.Properties());
    }
}
