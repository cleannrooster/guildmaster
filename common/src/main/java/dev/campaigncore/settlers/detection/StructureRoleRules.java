package dev.campaigncore.settlers.detection;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.data.CodecReloadListener;
import dev.campaigncore.settlers.settlement.StructureRole;
import dev.architectury.registry.ReloadListenerRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/// Datapack mapping from structure template ids to settlement roles
/// (`data/<ns>/structure_roles/<village_type>.json`). Rules are ordered substring matches — robust
/// against template-name variants (small_house_1..8) and extendable to other village biomes or
/// modded villages without code changes.
///
/// JSON shape: `{ "rules": [ { "contains": "town_center", "role": "plaza" }, ... ] }`
public record StructureRoleRules(List<Rule> rules) {

    public record Rule(String contains, StructureRole role) {
        public static final Codec<Rule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("contains").forGetter(Rule::contains),
                StructureRole.CODEC.fieldOf("role").forGetter(Rule::role)
        ).apply(instance, Rule::new));
    }

    public static final Codec<StructureRoleRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(StructureRoleRules::rules)
    ).apply(instance, StructureRoleRules::new));

    private static final Map<ResourceLocation, StructureRoleRules> RULE_SETS = new ConcurrentHashMap<>();

    public static void register() {
        ReloadListenerRegistry.register(PackType.SERVER_DATA,
                new CodecReloadListener<>("structure_roles", CODEC, parsed -> {
                    RULE_SETS.clear();
                    RULE_SETS.putAll(parsed);
                }),
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, "structure_roles"));
    }

    /// Rule set keyed by the source structure's own namespace ("minecraft", "ctov",
    /// "towns_and_towers", ...) -> settlers:<namespace>.json. A mod with no matching file (nothing
    /// authored for it yet, or its structures follow no discernible naming convention) falls back
    /// entirely to StructureClassifier's block-scan heuristic — degraded classification, never a
    /// crash or a skipped conversion.
    public static Optional<StructureRoleRules> forVillageType(String villageType) {
        return Optional.ofNullable(RULE_SETS.get(
                ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, villageType)));
    }

    public Optional<StructureRole> classify(ResourceLocation template) {
        String path = template.toString();
        for (Rule rule : this.rules) {
            if (path.contains(rule.contains())) {
                return Optional.of(rule.role());
            }
        }
        return Optional.empty();
    }
}
