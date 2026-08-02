package dev.campaigncore.settlers.compat;

import dev.architectury.platform.Platform;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.settlement.ReeveTheme;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/// Optional integration with the Frontier Armaments mod. When it is installed, a settlement's guards
/// wear the armor set that matches the settlement's {@link ReeveTheme} instead of their default vanilla
/// gear — a Lawful town's guards wear Templar plate, a Criminal one's wear Thief leathers, etc. When
/// the mod is absent, nothing here does anything and guards keep their datapack equipment.
///
/// This is a soft dependency: presence is checked at runtime via Architectury's mod list, and items are
/// resolved by id so a missing item simply leaves that slot untouched. No Frontier Armaments class is
/// referenced directly, so the mod need not be on the compile/runtime classpath.
public final class FrontierArmaments {
    public static final String MOD_ID = "frontier_armaments";

    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    private static Boolean present;

    private FrontierArmaments() {
    }

    public static boolean isPresent() {
        if (present == null) {
            present = Platform.isModLoaded(MOD_ID);
        }
        return present;
    }

    /// The Frontier Armaments armor set name for a civic theme. Best-effort thematic mapping; a single
    /// place to retune. Every entry is a real 4-piece set in that mod.
    public static String setFor(ReeveTheme theme) {
        return switch (theme) {
            case CIVIL -> "peasant";
            case LAWFUL -> "templar";
            case MILITARY -> "juggernaut";
            case RELIGIOUS -> "sanguine";
            case MERCANTILE -> "kintsugi";
            case AGRARIAN -> "primalist";
            case INDUSTRIAL -> "artillerist";
            case OCCULT -> "battlemage";
            case CRIMINAL -> "thief";
        };
    }

    /// True if the settler already wears a Frontier Armaments piece, used to avoid re-equipping every
    /// population pass.
    public static boolean alreadyEquipped(SettlerEntity settler) {
        ItemStack chest = settler.getItemBySlot(EquipmentSlot.CHEST);
        return !chest.isEmpty()
                && BuiltInRegistries.ITEM.getKey(chest.getItem()).getNamespace().equals(MOD_ID);
    }

    /// Dresses a guard in the full armor set for its settlement's theme. No-op when the mod is absent or
    /// none of the set's items resolve. Drop chances are zeroed — this is costume, like all Settler gear.
    public static void equipGuard(SettlerEntity guard, ReeveTheme theme) {
        if (!isPresent()) {
            return;
        }
        String set = setFor(theme);
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            Item item = itemFor(set, slot);
            if (item != null) {
                guard.setItemSlot(slot, new ItemStack(item));
                guard.setDropChance(slot, 0.0f);
            }
        }
    }

    private static Item itemFor(String set, EquipmentSlot slot) {
        if(slot.equals(EquipmentSlot.LEGS)) {
            return Items.CHAINMAIL_LEGGINGS;
        }
        String piece = switch (slot) {
            case HEAD -> "helmet";
            case CHEST -> "chestplate";
            case LEGS -> "leggings";
            case FEET -> "boots";
            default -> null;
        };
        if (piece == null) {
            return null;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MOD_ID, set + "_" + piece);
        return BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }
}
