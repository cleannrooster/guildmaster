package dev.campaigncore.settlers.registry;

import dev.campaigncore.CampaignCore;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.platform.PlatformHelper;
import dev.architectury.registry.CreativeTabRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;
import java.util.stream.Stream;

public final class SettlersItems {
    // Created against campaign_core's mod id so NeoForge can resolve the mod event bus (see
    // SettlersEntities); ids stay in the settlers: namespace via explicit ResourceLocations.
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(CampaignCore.MOD_ID, Registries.ITEM);

    // Spawn-egg construction differs per loader (Fabric SpawnEggItem vs NeoForge DeferredSpawnEggItem),
    // so it goes through @ExpectPlatform.
    public static final RegistrySupplier<Item> SETTLER_SPAWN_EGG =
            ITEMS.register(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "settler_spawn_egg"),
                    () -> PlatformHelper.createSpawnEgg(SettlersEntities.SETTLER, 0x8a6a4f, 0xd8c29a));

    public static final RegistrySupplier<Item> WAGON_REPAIR_BENCH =
            ITEMS.register(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "wagon_repair_bench"),
                    () -> new BlockItem(SettlersBlocks.WAGON_REPAIR_BENCH.get(), new Item.Properties()));

    /// Preserved settlement food: the player-facing reward that daily surplus converts into. Edible, but
    /// not always (only when actually hungry), representing a real meal's worth of provisions.

    /// The Settlement Ledger — right-click to scan nearby settlements and request provision deliveries.

    private SettlersItems() {
    }

    /// The dev spawn egg joins the vanilla spawn-eggs tab; the wagon repair bench joins building
    /// blocks, matching how a player would expect to find a placeable decorative block.
    public static void appendToCreativeTabs() {
        CreativeTabRegistry.appendStack(CreativeModeTabs.SPAWN_EGGS, Stream.<Supplier<ItemStack>>of(
                () -> new ItemStack(SETTLER_SPAWN_EGG.get())));
        CreativeTabRegistry.appendStack(CreativeModeTabs.BUILDING_BLOCKS, Stream.<Supplier<ItemStack>>of(
                () -> new ItemStack(WAGON_REPAIR_BENCH.get())));
    }
}
