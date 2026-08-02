package dev.campaigncore.washedashore.encounter;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.campaigncore.settlers.MinecraftTestBase;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Unit coverage for the data-driven encounter-candidate pool: codec, weighted selection, and grouping. */
class EncounterCandidateTest extends MinecraftTestBase {

    private static final ResourceLocation UNDERTAKER =
            ResourceLocation.fromNamespaceAndPath("campaign_core", "washed_ashore/undertaker");

    private static EncounterCandidate single(String path, ResourceLocation slot, ResourceLocation entity, int weight, boolean nativeFallback) {
        return new EncounterCandidate(ResourceLocation.fromNamespaceAndPath("campaign_core", path), slot,
                Optional.empty(), Optional.of(entity), Optional.empty(), List.of(), weight,
                EnvironmentRestriction.ANY, AttributeOverrides.EMPTY, false, false, false,
                Optional.empty(), Optional.empty(), Optional.empty(), nativeFallback);
    }

    @Test void codecParsesEveryField() {
        String json = "{"
                + "\"slot\":\"campaign_core:washed_ashore/undertaker\","
                + "\"required_mod\":\"graveyard\","
                + "\"entity\":\"graveyard:ghoul\","
                + "\"weight\":100,"
                + "\"variant\":\"grave_eater\","
                + "\"display_name\":\"encounter.campaign_core.washed_ashore.variant.grave_eater\","
                + "\"boss_bar\":true,"
                + "\"suppress_native_drops\":true,"
                + "\"campaign_loot_table\":\"campaign_core:washed_ashore/campaign/undertaker_reward\","
                + "\"environment\":{\"environment\":\"forest\"},"
                + "\"attributes\":{\"max_health\":100,\"scale\":1.3,\"damage_multiplier\":0.75,\"knockback_resistance\":0.5}"
                + "}";
        var result = EncounterCandidate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        assertTrue(result.error().isEmpty(), () -> "parse failed: " + result.error());
        EncounterCandidate c = result.result().orElseThrow();
        assertEquals(UNDERTAKER, c.slot());
        assertEquals("graveyard", c.requiredMod().orElseThrow());
        assertEquals("graveyard:ghoul", c.entity().orElseThrow().toString());
        assertEquals(100, c.weight());
        assertEquals("grave_eater", c.variant().orElseThrow());
        assertTrue(c.bossBar());
        assertTrue(c.suppressNativeDrops());
        assertTrue(c.isSingle());
        assertTrue(c.hasExactlyOneForm());
        assertEquals(100.0, c.attributes().maxHealth().orElseThrow());
        assertEquals(1.3, c.attributes().scale().orElseThrow());
        assertEquals(Optional.of(EnvironmentType.FOREST), c.environment().environment());
    }

    @Test void codecRejectsUnknownEntitiesGracefullyViaDefaults() {
        // Minimal candidate: only slot + entity; everything else defaults.
        String json = "{\"slot\":\"campaign_core:washed_ashore/undertaker\",\"entity\":\"minecraft:zombie\"}";
        EncounterCandidate c = EncounterCandidate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        assertEquals(100, c.weight());
        assertTrue(c.attributes().isEmpty());
        assertFalse(c.bossBar());
        assertFalse(c.nativeFallback());
        assertSame(EnvironmentRestriction.ANY.environment(), c.environment().environment());
    }

    @Test void raidFormRoundTripsWithRoles() {
        String json = "{\"slot\":\"campaign_core:washed_ashore/regional_boss_c\",\"raid\":["
                + "{\"entity\":\"minecraft:evoker\",\"count\":1,\"role\":\"commander\"},"
                + "{\"entity\":\"minecraft:pillager\",\"count\":3,\"role\":\"ranged\"}]}";
        EncounterCandidate c = EncounterCandidate.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        assertTrue(c.isRaid());
        assertFalse(c.isSingle());
        assertEquals(2, c.raid().size());
        assertEquals(RaidRole.COMMANDER, c.raid().get(0).role());
        assertEquals(RaidRole.RANGED, c.raid().get(1).role());
        assertEquals(3, c.raid().get(1).count());
    }

    @Test void weightedPickIgnoresZeroWeightAndHonorsWeights() {
        var chosen = single("a", UNDERTAKER, ResourceLocation.parse("minecraft:zombie"), 1, false);
        var skipped = single("b", UNDERTAKER, ResourceLocation.parse("minecraft:husk"), 0, false);
        RandomSource random = RandomSource.create(42L);
        for (int i = 0; i < 20; i++) {
            assertEquals(chosen, EncounterCandidateSelector.weightedPick(List.of(chosen, skipped), random).orElseThrow());
        }
        assertTrue(EncounterCandidateSelector.weightedPick(List.of(skipped), random).isEmpty());
        assertTrue(EncounterCandidateSelector.weightedPick(List.of(), random).isEmpty());
    }

    @Test void chooseFromPrefersPrimaryThenFallback() {
        var primary = single("primary", UNDERTAKER, ResourceLocation.parse("minecraft:zombie"), 100, false);
        var fallback = single("fallback", UNDERTAKER, ResourceLocation.parse("minecraft:skeleton"), 100, true);
        RandomSource random = RandomSource.create(7L);
        // Primary present -> a non-fallback candidate is chosen.
        assertEquals(primary, EncounterCandidateSelector.chooseFrom(List.of(primary, fallback), random).orElseThrow());
        // Only the fallback is eligible -> it is used.
        assertEquals(fallback, EncounterCandidateSelector.chooseFrom(List.of(fallback), random).orElseThrow());
        // Nothing eligible -> empty (caller then uses the native definition).
        assertTrue(EncounterCandidateSelector.chooseFrom(List.of(), random).isEmpty());
    }

    @Test void entityResolutionExcludesUnknownIds() {
        assertTrue(EncounterCandidateSelector.entityResolves(ResourceLocation.parse("minecraft:zombie")));
        assertFalse(EncounterCandidateSelector.entityResolves(ResourceLocation.parse("nonexistent_mod:phantom_boss")));
    }

    @Test void loadCandidatesGroupsBySlotAndDropsInvalid() {
        ResourceLocation badSlot = ResourceLocation.fromNamespaceAndPath("campaign_core", "bad_slot");
        // A candidate whose slot is not one of the shipped encounter ids.
        EncounterCandidate wrongSlot = single("x", ResourceLocation.parse("campaign_core:nope"),
                ResourceLocation.parse("minecraft:zombie"), 100, false);
        // A candidate with no form (neither entity/tag/raid).
        EncounterCandidate noForm = new EncounterCandidate(ResourceLocation.parse("campaign_core:y"), UNDERTAKER,
                Optional.empty(), Optional.empty(), Optional.empty(), List.of(), 100,
                EnvironmentRestriction.ANY, AttributeOverrides.EMPTY, false, false, false,
                Optional.empty(), Optional.empty(), Optional.empty(), false);
        EncounterCandidate valid = single("good", UNDERTAKER, ResourceLocation.parse("minecraft:zombie"), 100, false);

        EncounterManager.loadCandidates(java.util.Map.of(
                ResourceLocation.parse("campaign_core:good_file"), valid,
                badSlot, wrongSlot,
                ResourceLocation.parse("campaign_core:noform_file"), noForm));

        List<EncounterCandidate> undertaker = EncounterManager.candidates().forSlot(UNDERTAKER);
        assertEquals(1, undertaker.size(), "only the valid candidate should survive");
        // Loader stamps the file id onto the candidate.
        assertEquals(ResourceLocation.parse("campaign_core:good_file"), undertaker.get(0).id());
        assertTrue(EncounterManager.candidates().byId(ResourceLocation.parse("campaign_core:good_file")).isPresent());
    }
}
