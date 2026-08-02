package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.economy.SettlementEconomyState;
import dev.campaigncore.settlers.production.ProductionState;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.ResidentEntry;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementManager;
import dev.campaigncore.settlers.settlement.SettlementNames;
import dev.campaigncore.settlers.settlement.SettlementLaborModel;
import dev.campaigncore.settlers.settlement.SettlementProfile;
import dev.campaigncore.settlers.settlement.SettlerPopulator;
import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.StructureRole;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.ThreatState;
import dev.campaigncore.settlers.situation.SituationRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/// Orchestrates one village -> settlement conversion: classify pieces, run the reinterpretation
/// pass (vanilla villages have no authority building, guard post, or inn — we *assign* those
/// functions), generate anchors, derive the settlement profile, author the roster, register, and
/// optionally clear vanilla villagers.
///
/// This class is the prototype adapter around the generic settlement framework: everything it
/// produces (Settlement, roles, anchors, roster) is structure-agnostic.
public final class SettlementConverter {
    private SettlementConverter() {
    }

    public static Settlement convert(ServerLevel level, StructureStart start, String villageType) {
        List<StructureAssignment> structures =
                new ArrayList<>(StructureClassifier.classify(level, start, villageType));
        BoundingBox bounds = start.getBoundingBox();
        BlockPos center = findCenter(level, structures, bounds);

        reinterpretRoles(structures, center);

        AnchorTable anchors = AnchorGenerator.generate(level, structures, center);

        // Settlement profile (beta: derived from what the village actually contains).
        boolean hasFarms = structures.stream().anyMatch(s -> s.role() == StructureRole.FARM);
        boolean hasWorkshop = structures.stream().anyMatch(s ->
                s.role() == StructureRole.WORKSHOP || s.role() == StructureRole.PROCESSING);
        boolean hasShrine = structures.stream().anyMatch(s -> s.role() == StructureRole.SHRINE);
        String primaryEconomy = hasFarms ? "agriculture" : (hasWorkshop ? "crafting" : "subsistence");
        String secondaryEconomy = "";
        String culture = hasShrine ? "roadside_shrine_tradition" : "frontier_folkways";

        // No vanilla village piece has a wagon-repair block, so unlike every other profession's
        // workstation this one needs to be placed, not scanned — see WagonRepairPlacer.

        SettlementProfile profile = new SettlementProfile(
                SettlementNames.generate(level.getRandom()),
                villageType,
                primaryEconomy,
                secondaryEconomy,
                "civilian_reeve",
                "independent_frontier",
                "hostile_mobs",
                // Staged by the situation system (M5); recorded in the profile from day one.
                "damaged_caravan_arrival",
                culture,
                "stable_but_alert");

        Settlement settlement = new Settlement(
                UUID.randomUUID(),
                profile,
                bounds,
                center,
                structures,
                anchors,
                buildRoster(level, structures, anchors),
                ThreatState.NORMAL,
                new ProductionState(),
                new SettlementEconomyState());
        settlement.recordChronicle(level.getGameTime(), "founded", "");

        SettlementManager manager = SettlementManager.get(level);
        manager.register(start.getChunkPos(), settlement);

        if (SettlersConfig.get().conversion.removeVillagersFromConvertedSettlements) {
            removeVillagers(level, bounds);
        }

        SettlersMod.LOGGER.info("Converted village at {} into settlement '{}' ({}): {} structures, roster of {}.",
                center, settlement.name(), settlement.id(), structures.size(), settlement.roster().size());

        SettlerPopulator.populate(level, settlement);
        SituationRunner.trigger(level, settlement);
        return settlement;
    }

    /// The settlement center is the plaza if one classified, otherwise the geometric center.
    private static BlockPos findCenter(ServerLevel level, List<StructureAssignment> structures, BoundingBox bounds) {
        BlockPos raw = structures.stream()
                .filter(s -> s.role() == StructureRole.PLAZA)
                .findFirst()
                .map(StructureAssignment::center)
                .orElse(bounds.getCenter());
        return level.hasChunkAt(raw)
                ? level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, raw)
                : raw;
    }

    /// The reinterpretation pass: vanilla layouts lack several settlement functions, so residences are
    /// re-assigned to fill them. Order matters — authority takes the largest house, the guard post the
    /// most peripheral, the inn the one nearest a road. At least one residence is always preserved.
    private static void reinterpretRoles(List<StructureAssignment> structures, BlockPos center) {
        promoteResidence(structures, StructureRole.AUTHORITY,
                Comparator.comparingLong(SettlementConverter::volume).reversed());
        promoteResidence(structures, StructureRole.GUARD_POST,
                Comparator.comparingDouble(s -> -s.center().distSqr(center)));
        List<BlockPos> roadCenters = structures.stream()
                .filter(s -> s.role() == StructureRole.ROAD)
                .map(StructureAssignment::center)
                .toList();
        if (!roadCenters.isEmpty()) {
            promoteResidence(structures, StructureRole.INN,
                    Comparator.comparingDouble(s -> roadCenters.stream()
                            .mapToDouble(road -> road.distSqr(s.center()))
                            .min()
                            .orElse(Double.MAX_VALUE)));
        }
        // Vanilla plains villages have no dedicated storage building; the residence closest to the
        // center doubles as the storehouse only if none exists at all.
        if (structures.stream().noneMatch(s -> s.role() == StructureRole.STORAGE || s.role() == StructureRole.GRANARY)) {
            promoteResidence(structures, StructureRole.STORAGE,
                    Comparator.comparingDouble(s -> s.center().distSqr(center)));
        }
    }

    private static void promoteResidence(List<StructureAssignment> structures, StructureRole newRole,
                                         Comparator<StructureAssignment> preference) {
        if (structures.stream().anyMatch(s -> s.role() == newRole)) {
            return;
        }
        long residences = structures.stream().filter(s -> s.role() == StructureRole.RESIDENCE).count();
        if (residences <= 1) {
            SettlersMod.LOGGER.info("Not enough residences to assign a {} — recorded as a layout gap.", newRole);
            return;
        }
        structures.stream()
                .filter(s -> s.role() == StructureRole.RESIDENCE)
                .min(preference)
                .ifPresent(chosen -> {
                    structures.remove(chosen);
                    structures.add(new StructureAssignment(chosen.template(), newRole, chosen.bounds()));
                    SettlersMod.LOGGER.info("Reinterpreted {} as {}.", chosen.template(), newRole);
                });
    }

    private static long volume(StructureAssignment structure) {
        BoundingBox box = structure.bounds();
        return (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
    }

    /// Absolute outer safety backstop, independent of permanentPopulationCap below — pathological
    /// input (a huge food/bed count from a modded structure) still can't produce an unbounded roster.
    private static final int MAX_ROSTER_SIZE = 60;
    /// A freshly converted settlement is never trimmed below this many residents even if the strict
    /// bed/food formula would allow fewer — a settlement with literally 1-2 people at the moment of
    /// conversion isn't a meaningful gameplay state. Deliberately small: this is a floor under the
    /// formula's result, not a replacement for it.
    private static final int MIN_POPULATION = 3;

    /// Roster scales with the settlement's actual workstations and roles to fill, not a fixed
    /// headcount: 1 reeve always, guards scaled by settlement size, and workers scaled by how many
    /// effective (per-chunk-capped — see WorkstationCounter) anchors of their trade exist. Civilians
    /// then fill out the population proportionally to the working population.
    private static List<ResidentEntry> buildRoster(ServerLevel level, List<StructureAssignment> structures,
                                                    AnchorTable anchors) {
        List<ResidentEntry> roster = new ArrayList<>();
        RandomSource random = level.getRandom();

        roster.add(entry("reeve"));

        int guards = Mth.clamp(structures.size() / 15, 2, 10);
        for (int i = 0; i < guards; i++) {
            roster.add(entry("militia"));
        }

        addWorkers(roster, anchors, "farmer");
        addWorkers(roster, anchors, "herder");
        addWorkers(roster, anchors, "fisher");
        addWorkers(roster, anchors, "shrine_keeper");

        boolean hasWorkshop = structures.stream().anyMatch(s -> s.role() == StructureRole.WORKSHOP
                || s.role() == StructureRole.ARMORY || s.role() == StructureRole.PROCESSING);
        // A settlement with a workshop but no scanned WORK anchor still warrants one smith; otherwise
        // the ratio is the shared labor model's (WORK anchors / 3, capped at 4).
        int smiths = Math.max(hasWorkshop ? 1 : 0, SettlementLaborModel.requiredWorkers(anchors, "smith"));
        for (int i = 0; i < smiths; i++) {
            roster.add(entry("smith"));
        }


        int workingPopulation = roster.size();
        int civilianBase = Math.max(4, workingPopulation * 2);
        int civilians = Math.min(40, civilianBase + random.nextInt(civilianBase / 2 + 1));
        for (int i = 0; i < civilians; i++) {
            roster.add(entry("civilian"));
        }

        int cap = Math.min(permanentPopulationCap(level, structures, anchors), MAX_ROSTER_SIZE);
        cap = Math.max(cap, Math.min(MIN_POPULATION, roster.size()));
        // Civilians are appended last, so trimming from the tail drops them before ever touching a
        // guard, worker, or the reeve.
        while (roster.size() > cap) {
            roster.remove(roster.size() - 1);
        }
        return roster;
    }
    public static final int MINIMUM_POPULATION = 12;

    /// permanentPopulationCap = floor(min(beds x 0.9, effectiveFoodCapacity)) — see
    /// {@link FarmSizeClassifier#supportablePopulation}.
    ///
    /// Beds come straight from the settlement's SLEEP anchors; the final roster still has its own
    /// MAX_ROSTER_SIZE guard. Farm tiers are summed across every
    /// FARM/PASTURE structure (plus hay) — see FarmSizeClassifier — and each tier feeds
    /// PEOPLE_PER_FARM_TIER people (a substantial food source, 3 tiers, sustains 12), then scaled up by the
    /// settlement's infirmary modifier (healthcare stretches the existing food supply further, rather
    /// than adding a food source of its own).
    private static int permanentPopulationCap(ServerLevel level, List<StructureAssignment> structures, AnchorTable anchors) {
        int beds = anchors.count(AnchorType.SLEEP);
        int farmTiers = FarmSizeClassifier.totalFoodTiers(level, structures, anchors);
        double infirmaryModifier = FarmSizeClassifier.infirmaryModifier(structures);
        int cap = FarmSizeClassifier.supportablePopulation(beds, farmTiers, infirmaryModifier);
        SettlersMod.LOGGER.info("Population cap inputs: beds={}, farmTiers={}, infirmaryModifier={} -> cap={}.",
                beds, farmTiers, infirmaryModifier, cap);
        return cap;
    }

    /// Appends the shared labor model's required worker count for a profession, each as a roster entry
    /// referencing the like-named profile. The profession→anchor ratios live in
    /// {@link SettlementLaborModel}, not here.
    private static void addWorkers(List<ResidentEntry> roster, AnchorTable anchors, String profileName) {
        int count = SettlementLaborModel.requiredWorkers(anchors, profileName);
        for (int i = 0; i < count; i++) {
            roster.add(entry(profileName));
        }
    }

    private static ResidentEntry entry(String profileName) {
        return new ResidentEntry(ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, profileName));
    }

    private static void removeVillagers(ServerLevel level, BoundingBox bounds) {
        AABB area = AABB.of(bounds).inflate(8.0);
        List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
        villagers.forEach(Villager::discard);
        if (!villagers.isEmpty()) {
            SettlersMod.LOGGER.info("Removed {} vanilla villagers from converted settlement bounds.", villagers.size());
        }
    }
}
