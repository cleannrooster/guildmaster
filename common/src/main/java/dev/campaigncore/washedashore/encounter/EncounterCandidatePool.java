package dev.campaigncore.washedashore.encounter;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Holds the datapack-loaded encounter-candidate pool, grouped by slot (encounter id); swapped atomically
 * on resource reload, mirroring {@link EncounterDefinitionRegistry}.
 */
public final class EncounterCandidatePool {
    private volatile Map<ResourceLocation, List<EncounterCandidate>> bySlot = Map.of();
    private volatile Map<ResourceLocation, EncounterCandidate> byId = Map.of();

    /** All candidates registered for {@code slot}, in load order (empty when none). */
    public List<EncounterCandidate> forSlot(ResourceLocation slot) {
        return bySlot.getOrDefault(slot, List.of());
    }

    /** Looks up a single candidate by its full id (used by {@code combat_encounter select}). */
    public Optional<EncounterCandidate> byId(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Slots that have at least one candidate (used for command suggestions). */
    public java.util.Set<ResourceLocation> slots() { return bySlot.keySet(); }

    public synchronized void replace(Map<ResourceLocation, List<EncounterCandidate>> grouped) {
        Map<ResourceLocation, List<EncounterCandidate>> slotCopy = Map.copyOf(grouped);
        java.util.Map<ResourceLocation, EncounterCandidate> ids = new java.util.HashMap<>();
        for (List<EncounterCandidate> list : slotCopy.values()) {
            for (EncounterCandidate candidate : list) ids.put(candidate.id(), candidate);
        }
        this.bySlot = slotCopy;
        this.byId = Map.copyOf(ids);
    }
}
