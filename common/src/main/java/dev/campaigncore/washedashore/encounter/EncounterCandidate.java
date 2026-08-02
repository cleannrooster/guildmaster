package dev.campaigncore.washedashore.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * One data-driven option for filling an encounter slot. A candidate names the {@code slot} (an existing
 * encounter id) it can fill and one of three <em>forms</em>: a single {@code entity}, an {@code entityTag}
 * (any currently-loaded member of a {@code campaign_core:washed_ashore/*} entity_type tag), or a
 * {@code raid} roster. It carries the tuning ({@link AttributeOverrides}), presentation (boss bar, display
 * name, narrative variant), loot handling, and an optional environment gate used to select among the pool.
 *
 * <p>Candidates whose {@code requiredMod} is absent or whose entity/raid ids don't resolve are excluded at
 * selection time, never at load time, so a missing content mod can never crash startup. When every option
 * is excluded the encounter falls back to the one candidate flagged {@link #nativeFallback()} (or, absent
 * that, to the slot's native {@link EncounterDefinition}).
 *
 * <p>The {@link #id()} is assigned from the datapack file path by the loader; it is not part of the JSON.
 */
public record EncounterCandidate(
        ResourceLocation id,
        ResourceLocation slot,
        Optional<String> requiredMod,
        Optional<ResourceLocation> entity,
        Optional<ResourceLocation> entityTag,
        List<EncounterDefinition.RaidMember> raid,
        int weight,
        EnvironmentRestriction environment,
        AttributeOverrides attributes,
        boolean bossBar,
        boolean suppressNativeDrops,
        boolean persistence,
        Optional<String> displayName,
        Optional<String> variant,
        Optional<ResourceLocation> campaignLootTable,
        boolean nativeFallback
) {
    /** Placeholder id used before the loader stamps the file's resource id via {@link #withId}. */
    private static final ResourceLocation UNASSIGNED = ResourceLocation.fromNamespaceAndPath("campaign_core", "unassigned");

    /** Codec for the on-disk shape — everything but {@link #id()}, which comes from the file path. */
    public static final Codec<EncounterCandidate> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("slot").forGetter(EncounterCandidate::slot),
            Codec.STRING.optionalFieldOf("required_mod").forGetter(EncounterCandidate::requiredMod),
            ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(EncounterCandidate::entity),
            ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(EncounterCandidate::entityTag),
            EncounterDefinition.RaidMember.CODEC.listOf().optionalFieldOf("raid", List.of()).forGetter(EncounterCandidate::raid),
            Codec.INT.optionalFieldOf("weight", 100).forGetter(EncounterCandidate::weight),
            EnvironmentRestriction.CODEC.optionalFieldOf("environment", EnvironmentRestriction.ANY).forGetter(EncounterCandidate::environment),
            AttributeOverrides.CODEC.optionalFieldOf("attributes", AttributeOverrides.EMPTY).forGetter(EncounterCandidate::attributes),
            Codec.BOOL.optionalFieldOf("boss_bar", false).forGetter(EncounterCandidate::bossBar),
            Codec.BOOL.optionalFieldOf("suppress_native_drops", false).forGetter(EncounterCandidate::suppressNativeDrops),
            Codec.BOOL.optionalFieldOf("persistence", false).forGetter(EncounterCandidate::persistence),
            Codec.STRING.optionalFieldOf("display_name").forGetter(EncounterCandidate::displayName),
            Codec.STRING.optionalFieldOf("variant").forGetter(EncounterCandidate::variant),
            ResourceLocation.CODEC.optionalFieldOf("campaign_loot_table").forGetter(EncounterCandidate::campaignLootTable),
            Codec.BOOL.optionalFieldOf("native_fallback", false).forGetter(EncounterCandidate::nativeFallback)
    ).apply(instance, EncounterCandidate::fromCodec));

    private static EncounterCandidate fromCodec(ResourceLocation slot, Optional<String> requiredMod,
            Optional<ResourceLocation> entity, Optional<ResourceLocation> entityTag,
            List<EncounterDefinition.RaidMember> raid, int weight, EnvironmentRestriction environment,
            AttributeOverrides attributes, boolean bossBar, boolean suppressNativeDrops, boolean persistence,
            Optional<String> displayName, Optional<String> variant, Optional<ResourceLocation> campaignLootTable,
            boolean nativeFallback) {
        return new EncounterCandidate(UNASSIGNED, slot, requiredMod, entity, entityTag, raid, weight, environment,
                attributes, bossBar, suppressNativeDrops, persistence, displayName, variant, campaignLootTable, nativeFallback);
    }

    public EncounterCandidate {
        raid = raid == null ? List.of() : List.copyOf(raid);
        if (weight < 0) weight = 0;
        if (environment == null) environment = EnvironmentRestriction.ANY;
        if (attributes == null) attributes = AttributeOverrides.EMPTY;
    }

    /** Returns a copy stamped with the datapack file id (called by the loader). */
    public EncounterCandidate withId(ResourceLocation resourceId) {
        return new EncounterCandidate(resourceId, slot, requiredMod, entity, entityTag, raid, weight, environment,
                attributes, bossBar, suppressNativeDrops, persistence, displayName, variant, campaignLootTable, nativeFallback);
    }

    public boolean isRaid() { return !raid.isEmpty(); }
    public boolean isSingle() { return entity.isPresent() || entityTag.isPresent(); }
    /** True when exactly one form (single entity, entity tag, or raid) is configured. */
    public boolean hasExactlyOneForm() {
        int forms = (entity.isPresent() ? 1 : 0) + (entityTag.isPresent() ? 1 : 0) + (isRaid() ? 1 : 0);
        return forms == 1;
    }
}
