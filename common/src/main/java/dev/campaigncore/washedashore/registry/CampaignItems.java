package dev.campaigncore.washedashore.registry;

import dev.architectury.event.events.common.LootEvent;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.campaigncore.CampaignCore;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import java.util.Set;

public final class CampaignItems {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(CampaignCore.MOD_ID,Registries.ITEM);
    public static final RegistrySupplier<Item> DREAD_FRAGMENT=ITEMS.register("dread_fragment",
            ()->new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistrySupplier<Item> WRITHING_FRAGMENT=ITEMS.register("writhing_fragment",
            ()->new Item(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    /** Prestige challenge token for Washed Ashore: re-summons the Sculken Raven at full power. */
    public static final RegistrySupplier<Item> BLIGHT_FRAGMENT=ITEMS.register("blight_fragment",
            ()->new Item(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
    private static final Set<ResourceLocation> DUNGEON_LOOT=Set.of(
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),
            ResourceLocation.parse("minecraft:chests/stronghold_corridor"),
            ResourceLocation.parse("minecraft:chests/woodland_mansion"),
            ResourceLocation.parse("minecraft:chests/ancient_city"));

    private CampaignItems(){}

    public static void register(){
        ITEMS.register();
        LootEvent.MODIFY_LOOT_TABLE.register((key,table,builtin)->{
            if(!DUNGEON_LOOT.contains(key.location()))return;
            table.addPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(.025f))
                    .add(LootItem.lootTableItem(DREAD_FRAGMENT.get())));
            table.addPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1))
                    .when(LootItemRandomChanceCondition.randomChance(.025f))
                    .add(LootItem.lootTableItem(WRITHING_FRAGMENT.get())));
        });
    }
}
