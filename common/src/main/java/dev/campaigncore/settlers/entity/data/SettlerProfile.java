package dev.campaigncore.settlers.entity.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.campaigncore.settlers.settlement.AnchorType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/// Datapack-defined identity of a Settler (`data/<ns>/settler_profiles/*.json`). Everything that
/// varies between Settlers is data here — the entity class itself is generic. The profile id is the
/// JSON file's resource location.
///
/// `texture` is an explicit skin override (rare — for a specific, named NPC); leave it empty (the
/// default) and the entity instead gets a random base skin from `data/<ns>/base_skins/*.json`, chosen
/// once and persisted for that entity's lifetime. Any player-format (64x64) skin works, override or
/// random, since the Settler rig uses the vanilla player UV layout.
///
/// `jobOverlay` is a second texture of the same dimensions, rendered on top of the base skin using
/// only the vanilla skin format's second-layer regions (hat/jacket/sleeves/pant-legs) — the same
/// mechanism vanilla skins already use for their own overlay layer — so job identity reads
/// independently of which of the random base skins a given Settler happens to have.
///
/// `workstation` is an explicit override of the anchor-type preference used to place this profile at
/// a work anchor; leave it empty (the default) to fall back to {@link ProfessionWorkstations}'s lookup
/// by {@link #profession()} — only set it for a profession name that table doesn't already cover.
public record SettlerProfile(
        SettlerRole role,
        String profession,
        String faction,
        Optional<ResourceLocation> texture,
        Optional<ResourceLocation> jobOverlay,
        Optional<ResourceLocation> equipmentProfile,
        Optional<String> displayName,
        List<AnchorType> workstation
) {
    public static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public static final Codec<SettlerProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SettlerRole.CODEC.fieldOf("role").forGetter(SettlerProfile::role),
            Codec.STRING.optionalFieldOf("profession", "laborer").forGetter(SettlerProfile::profession),
            Codec.STRING.optionalFieldOf("faction", "independent_frontier").forGetter(SettlerProfile::faction),
            ResourceLocation.CODEC.optionalFieldOf("texture").forGetter(SettlerProfile::texture),
            ResourceLocation.CODEC.optionalFieldOf("job_overlay").forGetter(SettlerProfile::jobOverlay),
            ResourceLocation.CODEC.optionalFieldOf("equipment_profile").forGetter(SettlerProfile::equipmentProfile),
            Codec.STRING.optionalFieldOf("display_name").forGetter(SettlerProfile::displayName),
            AnchorType.CODEC.listOf().optionalFieldOf("workstation", List.of()).forGetter(SettlerProfile::workstation)
    ).apply(instance, SettlerProfile::new));

    /// Resolves this profile's workstation preference, falling back to the profession-keyed default.
    public List<AnchorType> resolveWorkstation() {
        return this.workstation.isEmpty() ? ProfessionWorkstations.forProfession(this.profession) : this.workstation;
    }
}
