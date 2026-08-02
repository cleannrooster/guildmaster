package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/// The civic character of a settlement, expressed through its reeve: the office's title (e.g. Sheriff
/// vs. Guildmaster vs. Magister) and the armor/clothing they wear. A settlement's theme is a
/// deterministic function of its identity — seeded by its UUID and biased toward what the settlement
/// actually is (a farming village leans Agrarian, a workshop town Industrial, a shrine town Religious)
/// — so it's stable across reloads and reeve replacements without any extra persisted state.
public enum ReeveTheme {
    // baseWeight biases the random pick before settlement-specific bonuses are added below.
    CIVIL(3, ArmorWeight.MEDIUM, List.of(mat(Material.LEATHER, 1)),
            titles("administrator", "superintendent", "overseer", "provost", "bailiff", "steward")),
    LAWFUL(2, ArmorWeight.MEDIUM, List.of(mat(Material.LEATHER, 2), mat(Material.IRON, 2)),
            titles("sheriff", "marshal", "constable", "warden", "arbiter")),
    MILITARY(1, ArmorWeight.HEAVY, List.of(mat(Material.IRON, 4), mat(Material.CHAINMAIL, 4), mat(Material.DIAMOND, 1)),
            titles("captain", "commander", "castellan", "commandant", "master_at_arms")),
    RELIGIOUS(1, ArmorWeight.LIGHT, List.of(mat(Material.CHAINMAIL, 2), mat(Material.LEATHER, 3)),
            titles("prior", "deacon", "head_inquisitor", "justiciar", "elder")),
    MERCANTILE(1, ArmorWeight.LIGHT, List.of(mat(Material.GOLD, 3), mat(Material.DIAMOND, 1)),
            titles("guildmaster", "tollmaster", "provisioner", "roadmaster", "quartermaster")),
    AGRARIAN(1, ArmorWeight.LIGHT, List.of(mat(Material.LEATHER, 1)),
            titles("first_elder", "speaker", "foreman", "hearthkeeper", "fieldmaster")),
    INDUSTRIAL(1, ArmorWeight.MEDIUM, List.of(mat(Material.IRON, 2), mat(Material.LEATHER, 2)),
            titles("works_master", "chief_engineer", "workshop_overseer", "ironmaster", "master_smith")),
    OCCULT(1, ArmorWeight.MEDIUM, List.of(mat(Material.GOLD, 2), mat(Material.LEATHER, 3)),
            titles("magister", "first_adept", "augur", "oracle", "lorekeeper")),
    CRIMINAL(1, ArmorWeight.MEDIUM, List.of(mat(Material.LEATHER, 3), mat(Material.CHAINMAIL, 2)),
            titles("boss", "kingpin", "patron", "old_hand", "strongman"));

    private final int baseWeight;
    private final ArmorWeight armorWeight;
    private final List<WeightedMaterial> materials;
    private final List<String> titleKeys;

    ReeveTheme(int baseWeight, ArmorWeight armorWeight, List<WeightedMaterial> materials, List<String> titleKeys) {
        this.baseWeight = baseWeight;
        this.armorWeight = armorWeight;
        this.materials = materials;
        this.titleKeys = titleKeys;
    }

    /// Translation key for this theme's display name (e.g. "settlers.reeve.theme.lawful").
    public String translationKey() {
        return "settlers.reeve.theme." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /// Derives the reeve's full identity (theme + title + armor material) for a settlement. Seeded by
    /// the settlement UUID so it is identical every time — a replacement reeve inherits the same office.
    public static ReeveIdentity identify(Settlement settlement) {
        RandomSource random = RandomSource.create(seed(settlement));
        ReeveTheme theme = pickTheme(settlement, random);
        String titleKey = theme.titleKeys.get(random.nextInt(theme.titleKeys.size()));
        Material material = theme.pickMaterial(random);
        return new ReeveIdentity(theme, titleKey, material);
    }

    /// Names and dresses a settlement's reeve according to its theme. Overrides only the armor slots the
    /// theme covers and the custom name; the reeve's book-of-office (mainhand, from its equipment
    /// profile) and everything else are left untouched. Server-side; the entity persists the result.
    public static void apply(SettlerEntity reeve, Settlement settlement) {
        ReeveIdentity identity = identify(settlement);
        reeve.setCustomName(Component.translatable(identity.titleKey()));
        for (EquipmentSlot slot : identity.theme().armorWeight.slots()) {
            Item item = armorItem(identity.material(), slot);
            if (item != null) {
                reeve.setItemSlot(slot, new ItemStack(item));
                reeve.setDropChance(slot, 0.0f);
            }
        }
    }

    private static long seed(Settlement settlement) {
        return settlement.id().getMostSignificantBits() ^ settlement.id().getLeastSignificantBits();
    }

    /// Weighted theme pick: every theme is possible, but the settlement's farms/workshops/shrines and
    /// economy heavily favor the matching theme so most reeves read as thematically appropriate.
    private static ReeveTheme pickTheme(Settlement settlement, RandomSource random) {
        boolean hasFarms = settlement.structures().stream().anyMatch(a ->
                a.role() == StructureRole.FARM || a.role() == StructureRole.PASTURE);
        boolean hasWorkshop = settlement.structures().stream().anyMatch(a ->
                a.role() == StructureRole.WORKSHOP || a.role() == StructureRole.PROCESSING
                        || a.role() == StructureRole.ARMORY);
        boolean hasShrine = settlement.structures().stream().anyMatch(a -> a.role() == StructureRole.SHRINE);
        String economy = settlement.profile().primaryEconomy();

        ReeveTheme[] all = values();
        int[] weights = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            weights[i] = all[i].baseWeight;
        }
        if (hasFarms) {
            weights[AGRARIAN.ordinal()] += 6;
        }
        if (hasWorkshop) {
            weights[INDUSTRIAL.ordinal()] += 6;
        }
        if (hasShrine) {
            weights[RELIGIOUS.ordinal()] += 6;
        }
        if ("agriculture".equals(economy)) {
            weights[AGRARIAN.ordinal()] += 3;
        } else if ("wagon_repair".equals(economy)) {
            weights[INDUSTRIAL.ordinal()] += 3;
        }

        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        int roll = random.nextInt(total);
        for (int i = 0; i < all.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return all[i];
            }
        }
        return CIVIL;
    }

    private Material pickMaterial(RandomSource random) {
        int total = 0;
        for (WeightedMaterial weighted : this.materials) {
            total += weighted.weight();
        }
        int roll = random.nextInt(total);
        for (WeightedMaterial weighted : this.materials) {
            roll -= weighted.weight();
            if (roll < 0) {
                return weighted.material();
            }
        }
        return this.materials.get(0).material();
    }

    private static Item armorItem(Material material, EquipmentSlot slot) {
        return switch (material) {
            case LEATHER -> switch (slot) {
                case HEAD -> Items.LEATHER_HELMET;
                case CHEST -> Items.LEATHER_CHESTPLATE;
                case LEGS -> Items.LEATHER_LEGGINGS;
                case FEET -> Items.LEATHER_BOOTS;
                default -> null;
            };
            case IRON -> switch (slot) {
                case HEAD -> Items.IRON_HELMET;
                case CHEST -> Items.IRON_CHESTPLATE;
                case LEGS -> Items.IRON_LEGGINGS;
                case FEET -> Items.IRON_BOOTS;
                default -> null;
            };
            case CHAINMAIL -> switch (slot) {
                case HEAD -> Items.CHAINMAIL_HELMET;
                case CHEST -> Items.CHAINMAIL_CHESTPLATE;
                case LEGS -> Items.CHAINMAIL_LEGGINGS;
                case FEET -> Items.CHAINMAIL_BOOTS;
                default -> null;
            };
            case GOLD -> switch (slot) {
                case HEAD -> Items.GOLDEN_HELMET;
                case CHEST -> Items.GOLDEN_CHESTPLATE;
                case LEGS -> Items.GOLDEN_LEGGINGS;
                case FEET -> Items.GOLDEN_BOOTS;
                default -> null;
            };
            case DIAMOND -> switch (slot) {
                case HEAD -> Items.DIAMOND_HELMET;
                case CHEST -> Items.DIAMOND_CHESTPLATE;
                case LEGS -> Items.DIAMOND_LEGGINGS;
                case FEET -> Items.DIAMOND_BOOTS;
                default -> null;
            };
        };
    }

    private static WeightedMaterial mat(Material material, int weight) {
        return new WeightedMaterial(material, weight);
    }

    private static List<String> titles(String... keys) {
        return java.util.Arrays.stream(keys).map(key -> "settlers.reeve.title." + key).toList();
    }

    /// The resolved reeve identity for one settlement.
    public record ReeveIdentity(ReeveTheme theme, String titleKey, Material material) {
        public Component title() {
            return Component.translatable(this.titleKey);
        }
    }

    private record WeightedMaterial(Material material, int weight) {
    }

    /// Armor tiers available to a reeve. CHAINMAIL is the vanilla chain armor set.
    public enum Material {
        LEATHER, IRON, CHAINMAIL, GOLD, DIAMOND
    }

    /// How much of the reeve is armored/clothed. Light reads as garb (tunic + trousers), heavy as a full
    /// harness. FEET is left bare below HEAVY so the reeve keeps its profile's boots.
    public enum ArmorWeight {
        LIGHT(EquipmentSlot.CHEST, EquipmentSlot.LEGS),
        MEDIUM(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS),
        HEAVY(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

        private final List<EquipmentSlot> slots;

        ArmorWeight(EquipmentSlot... slots) {
            this.slots = List.of(slots);
        }

        public List<EquipmentSlot> slots() {
            return this.slots;
        }
    }
}
