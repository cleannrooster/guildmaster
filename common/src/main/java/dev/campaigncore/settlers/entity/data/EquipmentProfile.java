package dev.campaigncore.settlers.entity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/// Datapack-defined, deterministic gear set (`data/<ns>/equipment_profiles/*.json`). Equipment is
/// visual language for role and faction — militia get partial iron, the reeve gets fine clothes —
/// so there is intentionally no randomization here.
///
/// JSON shape: `{ "slots": { "head": "minecraft:iron_helmet", "mainhand": "minecraft:iron_sword" } }`
/// with the vanilla EquipmentSlot names (head/chest/legs/feet/mainhand/offhand).
public record EquipmentProfile(Map<EquipmentSlot, Item> slots) {

    public static final Codec<EquipmentProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(EquipmentSlot.CODEC, BuiltInRegistries.ITEM.byNameCodec())
                    .optionalFieldOf("slots", Map.of())
                    .forGetter(EquipmentProfile::slots)
    ).apply(instance, EquipmentProfile::new));

    /// Applies the gear. Drop chances are zeroed: Settler equipment is costume and role signal, not loot.
    public void apply(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            Item item = this.slots.get(slot);
            entity.setItemSlot(slot, item == null ? ItemStack.EMPTY : new ItemStack(item));
            if (entity instanceof net.minecraft.world.entity.Mob mob) {
                mob.setDropChance(slot, 0.0f);
            }
        }
    }
}
