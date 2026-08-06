package dev.campaigncore.washedashore.encounter;

import dev.architectury.platform.Platform;
import dev.campaigncore.CampaignCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Selects a weighted, mod-and-environment-eligible {@link EncounterCandidate} for a slot, resolves its
 * entity type, and applies its attribute/presentation overrides to a spawned mob. Missing mods or entities
 * are excluded (with a logged reason), never fatal — this is the single place the pool's "gracefully degrade
 * to the native encounter" contract lives.
 */
public final class EncounterCandidateSelector {
    private final EncounterCandidatePool pool;
    /** Cached {@code Platform.isModLoaded} results (mod set is fixed for a launch). */
    private final Map<String, Boolean> modPresence = new ConcurrentHashMap<>();

    public EncounterCandidateSelector(EncounterCandidatePool pool) { this.pool = pool; }

    public EncounterCandidatePool pool() { return pool; }

    /**
     * Picks a candidate for {@code slot} at {@code anchor}: filters the pool by mod/entity availability and
     * environment (logging each exclusion), then weighted-picks a non-fallback candidate, falling back to the
     * flagged native candidate and finally to {@link Optional#empty()} (caller then uses the native definition).
     */
    public Optional<EncounterCandidate> select(ServerLevel level, ResourceLocation slot, BlockPos anchor) {
        List<EncounterCandidate> candidates = pool.forSlot(slot);
        if (candidates.isEmpty()) return Optional.empty();
        List<EncounterCandidate> eligible = new ArrayList<>();
        for (EncounterCandidate candidate : candidates) {
            String reason = ineligibilityReason(level, candidate, anchor);
            if (reason == null) eligible.add(candidate);
            else CampaignCore.LOGGER.info("combat_candidate_excluded slot={} candidate={} reason={}", slot, candidate.id(), reason);
        }
        return chooseFrom(eligible, level.random);
    }

    /** Selects one exact entity from a slot, while retaining the normal availability/environment checks. */
    public Optional<EncounterCandidate> selectEntity(
            ServerLevel level, ResourceLocation slot, BlockPos anchor, ResourceLocation entityId
    ) {
        for (EncounterCandidate candidate : pool.forSlot(slot)) {
            if (candidate.entity().isPresent() && candidate.entity().get().equals(entityId)
                    && ineligibilityReason(level, candidate, anchor) == null) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** Selects one exact candidate while retaining all normal availability and environment checks. */
    public Optional<EncounterCandidate> selectCandidate(
            ServerLevel level,ResourceLocation slot,BlockPos anchor,ResourceLocation candidateId
    ) {
        return pool.byId(candidateId).filter(candidate->candidate.slot().equals(slot)
                &&ineligibilityReason(level,candidate,anchor)==null);
    }

    /**
     * Pure selection over an already-eligible list: weighted-pick a non-fallback candidate; if there are none,
     * use a fallback candidate; if the list is empty, return empty. Exposed for unit testing.
     */
    public static Optional<EncounterCandidate> chooseFrom(List<EncounterCandidate> eligible, RandomSource random) {
        List<EncounterCandidate> primary = new ArrayList<>();
        List<EncounterCandidate> fallbacks = new ArrayList<>();
        for (EncounterCandidate candidate : eligible) {
            (candidate.nativeFallback() ? fallbacks : primary).add(candidate);
        }
        Optional<EncounterCandidate> picked = weightedPick(primary, random);
        if (picked.isPresent()) return picked;
        // No primary option survived; prefer a data-defined native-fallback candidate, else nothing.
        Optional<EncounterCandidate> fallback = weightedPick(fallbacks, random);
        return fallback.isPresent() ? fallback : (fallbacks.isEmpty() ? Optional.empty() : Optional.of(fallbacks.get(0)));
    }

    /** Weighted random pick; candidates with weight 0 are ignored. Returns empty for an empty/zero-weight list. */
    public static Optional<EncounterCandidate> weightedPick(List<EncounterCandidate> list, RandomSource random) {
        int total = 0;
        for (EncounterCandidate candidate : list) total += Math.max(0, candidate.weight());
        if (total <= 0) return Optional.empty();
        int roll = random.nextInt(total);
        for (EncounterCandidate candidate : list) {
            roll -= Math.max(0, candidate.weight());
            if (roll < 0) return Optional.of(candidate);
        }
        return Optional.of(list.get(list.size() - 1));
    }

    /** Returns null when the candidate is eligible, or a short reason string for the exclusion log. */
    private String ineligibilityReason(ServerLevel level, EncounterCandidate candidate, BlockPos anchor) {
        if (candidate.requiredMod().isPresent() && !modPresent(candidate.requiredMod().get())) {
            return "missing_mod:" + candidate.requiredMod().get();
        }
        if (!candidate.hasExactlyOneForm()) return "invalid_form";
        if (candidate.isRaid()) {
            if (candidate.raid().stream().noneMatch(m -> entityResolves(m.entity()))) return "no_raid_entities";
        } else if (candidate.entity().isPresent()) {
            if (!entityResolves(candidate.entity().get())) return "unresolved_entity:" + candidate.entity().get();
        } else if (candidate.entityTag().isPresent()) {
            if (tagMembers(candidate.entityTag().get()).isEmpty()) return "empty_tag:" + candidate.entityTag().get();
        }
        if (!candidate.environment().matches(level, anchor)) return "environment_mismatch";
        return null;
    }

    /** Human-readable availability of a candidate ignoring environment (for the {@code list} command). */
    public String availability(EncounterCandidate candidate) {
        if (candidate.requiredMod().isPresent() && !modPresent(candidate.requiredMod().get())) {
            return "unavailable(missing_mod:" + candidate.requiredMod().get() + ")";
        }
        if (!candidate.hasExactlyOneForm()) return "unavailable(invalid_form)";
        if (candidate.isRaid()) {
            long ok = candidate.raid().stream().filter(m -> entityResolves(m.entity())).count();
            return ok == 0 ? "unavailable(no_raid_entities)" : "available(raid " + ok + "/" + candidate.raid().size() + ")";
        }
        if (candidate.entity().isPresent()) {
            return entityResolves(candidate.entity().get()) ? "available" : "unavailable(unresolved_entity)";
        }
        if (candidate.entityTag().isPresent()) {
            int n = tagMembers(candidate.entityTag().get()).size();
            return n == 0 ? "unavailable(empty_tag)" : "available(tag " + n + ")";
        }
        return "unavailable(no_form)";
    }

    public boolean modPresent(String modId) {
        return modPresence.computeIfAbsent(modId, Platform::isModLoaded);
    }

    /** True when {@code id} names a registered entity type (clean exclusion primitive, no PIG sentinel). */
    public static boolean entityResolves(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id).isPresent();
    }

    /** The currently-loaded entity types in a {@code campaign_core:washed_ashore/*} tag (missing ids absent). */
    public static List<EntityType<?>> tagMembers(ResourceLocation tagId) {
        TagKey<EntityType<?>> key = TagKey.create(Registries.ENTITY_TYPE, tagId);
        return BuiltInRegistries.ENTITY_TYPE.getTag(key)
                .map(named -> named.stream().map(Holder::value).toList())
                .orElse(List.of());
    }

    /** Resolves a single-form candidate's entity type (direct id or a random tag member). */
    public Optional<EntityType<?>> resolveEntityType(EncounterCandidate candidate, RandomSource random) {
        if (candidate.entity().isPresent()) {
            return BuiltInRegistries.ENTITY_TYPE.getOptional(candidate.entity().get());
        }
        if (candidate.entityTag().isPresent()) {
            List<EntityType<?>> members = tagMembers(candidate.entityTag().get());
            if (members.isEmpty()) return Optional.empty();
            return Optional.of(members.get(random.nextInt(members.size())));
        }
        return Optional.empty();
    }

    /** Applies attribute overrides, custom name, and persistence to a freshly-spawned candidate mob. */
    public void applyOverrides(Entity entity, EncounterCandidate candidate) {
        if (entity instanceof LivingEntity living && !candidate.attributes().isEmpty()) {
            candidate.attributes().applyTo(living);
        }
        candidate.displayName().ifPresent(name -> {
            entity.setCustomName(displayComponent(name));
            entity.setCustomNameVisible(true);
        });
        if (candidate.persistence() && entity instanceof Mob mob) {
            mob.setPersistenceRequired();
        }
    }

    /** A candidate display name: a translation key (contains a dot, no spaces) or a literal string. */
    public static Component displayComponent(String name) {
        return name.indexOf(' ') < 0 && name.indexOf('.') >= 0 ? Component.translatable(name) : Component.literal(name);
    }
}
