package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.settlement.StructureAssignment;
import dev.campaigncore.settlers.settlement.AnchorTable;
import dev.campaigncore.settlers.settlement.AnchorType;
import dev.campaigncore.settlers.settlement.StructureRole;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import java.util.List;

import static dev.campaigncore.settlers.detection.SettlementConverter.MINIMUM_POPULATION;

/// Computes a settlement's total "farm units" for population-cap accounting — see
/// {@link SettlementConverter}'s permanentPopulationCap — from three sources:
///
/// - FARM-role structures: sized small/medium/large (1/2/3 units) from a block census of farmland and
///   composters within their own bounds.
/// - PASTURE-role structures: a flat {@link #LARGE_FARM_UNITS} each — livestock is a food source, same
///   tier as a large crop field.
/// Hay bales are excluded: they are a finite emergency reserve consumed by the economy rather than
/// renewable farmland capacity.
///
/// INFIRMARY-role structures are handled separately, as a *percentage modifier* on the total rather
/// than a flat unit contribution — see {@link #infirmaryModifier}: healthcare doesn't grow food, it
/// improves how far the settlement's existing food supply stretches.
///
/// Composters no longer contribute farm score; planted crops provide full credit and empty farmland
/// grow food itself, but its bonemeal output accelerates growth across a wider area than one tile and
/// it represents the settlement's food-processing/waste-handling capacity (the design's storage and
/// food-preparation nodes) — costlier to build than a single tilled tile, so it's weighted well above
/// one. Farm-score is deliberately *not* capped per chunk the way
/// {@link WorkstationCounter} caps workstation anchors: a real farm field is *supposed* to
/// be a dense, contiguous cluster in a small area, so a per-chunk cap would undercount every ordinary
/// large one, not just gamed ones. Each structure's own bounding box (a jigsaw piece, inherently
/// small) is already the limiting factor on how much it can contribute — no separate runaway guard is
/// needed here, unlike workstation anchors which are scattered across a whole settlement's chunks.
public final class FarmSizeClassifier {
    /// What PASTURE structures contribute flat, matching a large farm's own tier.
    public static final int LARGE_FARM_UNITS = 3;
    /// Each INFIRMARY structure adds this much to the food-capacity percentage modifier.
    public static final double INFIRMARY_MODIFIER_PER_BUILDING = 0.10;
    /// The infirmary modifier never exceeds this, regardless of how many infirmaries exist.
    public static final double INFIRMARY_MODIFIER_CAP = 0.30;
    /// One abstract food tier produces six food per day. A substantial farm, pasture, or fishing
    /// site contributes three tiers, for eighteen food/residents of capacity.
    public static final double PEOPLE_PER_FARM_TIER = 6.0;

    private static final int SMALL_THRESHOLD = 8;
    private static final int MEDIUM_THRESHOLD = 16;
    private static final int LARGE_THRESHOLD = 32;

    /// The farm tier (0-3) implied by an aggregate farmland+composter count — the same small/medium/
    /// large tiering {@link #unitsFor} applies per FARM structure, exposed for the qualification-time
    /// estimate in {@link SettlementCandidateEvaluator} where only aggregate scan counts are available.
    public static int farmTier(int farmland, int plantedCrops) {
        int planted = Math.min(farmland, Math.max(0, plantedCrops));
        int doubledScore = planted * 2 + Math.max(0, farmland - planted);
        return farmSizeTierFromDoubledScore(doubledScore);
    }

    /// The permanent population a settlement can support: the lesser of what its beds and its food
    /// supply allow. beds x 0.9 leaves slack; food is farmTiers scaled by {@link #PEOPLE_PER_FARM_TIER}
    /// and stretched by the infirmary modifier. Shared by the conversion cap (authoritative, per-
    /// structure inputs) and the qualification gate (estimate, aggregate scan inputs).
    public static int supportablePopulation(int beds, int farmTiers, double infirmaryModifier) {
        return (int) Math.floor(Math.max(MINIMUM_POPULATION, Math.min(beds * 0.9, foodCapacity(farmTiers, infirmaryModifier)))) ;
    }

    /// The settlement's food-supply potential (in people-equivalents, which double as food units per
    /// day since consumption is one unit per resident per day): farm tiers scaled by
    /// {@link #PEOPLE_PER_FARM_TIER} and stretched by the infirmary modifier. This is the food side of
    /// {@link #supportablePopulation} without the bed constraint — the authoritative ceiling the
    /// production system scales food output against, so no farm thresholds are duplicated elsewhere.
    public static double foodCapacity(int farmTiers, double infirmaryModifier) {
        return farmTiers * PEOPLE_PER_FARM_TIER * (1.0 + infirmaryModifier);
    }
    /// Total farm units across the settlement: farm-size tiers, the flat pasture bonus, and hay-bale
    /// bonuses, all summed. Does *not* include the infirmary modifier — see {@link #infirmaryModifier}
    /// — which scales this total rather than adding to it.
    public static int totalFarmTiers(ServerLevel level, List<StructureAssignment> structures) {
        int total = 0;
        for (StructureAssignment structure : structures) {
            StructureRole role = structure.role();
            if (role == StructureRole.ROAD) {
                // Pure pathways carry no food/support relevance, and scanning every road piece in a
                // large settlement adds up; nothing else here has a reason to look at them either.
                continue;
            }
            if (role == StructureRole.PASTURE) {
                total += LARGE_FARM_UNITS;
            }
            total += unitsFor(level, structure.scanBounds(), role == StructureRole.FARM);
        }
        return total;
    }

    /// Total renewable-food tiers, including fishing infrastructure. One effective fishing anchor is
    /// treated like one substantial site (three tiers / twelve food per day), matching large farms and
    /// pastures. WorkstationCounter prevents dense duplicate anchors from inflating this capacity.
    public static int totalFoodTiers(ServerLevel level, List<StructureAssignment> structures, AnchorTable anchors) {
        return totalFarmTiers(level, structures)
                + WorkstationCounter.effectiveCount(anchors, AnchorType.FISHING) * LARGE_FARM_UNITS;
    }

    /// Percentage bonus to effectiveFoodCapacity from healthcare capacity: 10% per infirmary, capped
    /// at 30% (three infirmaries or more). A multiplier rather than a flat unit count, since an
    /// infirmary doesn't produce food — it improves how far the settlement's existing supply stretches.
    public static double infirmaryModifier(List<StructureAssignment> structures) {
        long infirmaries = structures.stream().filter(s -> s.role() == StructureRole.INFIRMARY).count();
        return Math.min(infirmaries * INFIRMARY_MODIFIER_PER_BUILDING, INFIRMARY_MODIFIER_CAP);
    }

    /// Food ceiling with crop maturity applied only to crop-farm tiers. Pastures remain fully
    /// productive regardless of nearby crop state.
    public static double operationalFoodCapacity(ServerLevel level, List<StructureAssignment> structures) {
        double effectiveTiers = 0.0;
        for (StructureAssignment structure : structures) {
            if (structure.role() == StructureRole.PASTURE) {
                effectiveTiers += LARGE_FARM_UNITS;
            } else if (structure.role() == StructureRole.FARM) {
                int tier = unitsFor(level, structure.scanBounds(), true);
                int[] crops = cropCounts(level, structure.scanBounds());
                double maturity = crops[0] == 0 ? 0.75 : 0.75 + 0.25 * crops[1] / (double) crops[0];
                effectiveTiers += tier * maturity;
            }
        }
        return effectiveTiers * PEOPLE_PER_FARM_TIER * (1.0 + infirmaryModifier(structures));
    }

    public static double operationalFoodCapacity(ServerLevel level, List<StructureAssignment> structures,
                                                 AnchorTable anchors) {
        double fishingTiers = WorkstationCounter.effectiveCount(anchors, AnchorType.FISHING)
                * LARGE_FARM_UNITS;
        return operationalFoodCapacity(level, structures)
                + fishingTiers * PEOPLE_PER_FARM_TIER * (1.0 + infirmaryModifier(structures));
    }

    public static boolean harvestOneMatureCrop(ServerLevel level, List<StructureAssignment> structures) {
        for (StructureAssignment structure : structures) {
            if (structure.role() != StructureRole.FARM) {
                continue;
            }
            BoundingBox bounds = structure.scanBounds();
            if (!level.hasChunksAt(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
                continue;
            }
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                    for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                        BlockPos pos = cursor.set(x, y, z).immutable();
                        BlockState state = level.getBlockState(pos);
                        if (state.is(BlockTags.CROPS) && isMatureCrop(state)
                                && level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
                            level.setBlock(pos, resetCropAge(state), 3);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /// One block census per FARM structure, computing its farmland/composter score.
    private static int unitsFor(ServerLevel level, BoundingBox bounds, boolean scoreFarmSize) {
        if (!level.hasChunksAt(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
            return 0;
        }
        int farmland = 0;
        int planted = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(Blocks.FARMLAND)) {
                        farmland++;
                        if (level.getBlockState(cursor.above()).is(BlockTags.CROPS)) {
                            planted++;
                        }
                    }
                }
            }
        }
        return scoreFarmSize ? farmTier(farmland, planted) : 0;
    }

    private static int[] cropCounts(ServerLevel level, BoundingBox bounds) {
        if (!level.hasChunksAt(bounds.minX(), bounds.minZ(), bounds.maxX(), bounds.maxZ())) {
            return new int[]{0, 0};
        }
        int planted = 0;
        int mature = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    BlockState state = level.getBlockState(cursor.set(x, y, z));
                    if (state.is(BlockTags.CROPS)
                            && level.getBlockState(cursor.below()).is(Blocks.FARMLAND)) {
                        planted++;
                        if (isMatureCrop(state)) {
                            mature++;
                        }
                    }
                }
            }
        }
        return new int[]{planted, mature};
    }

    private static boolean isMatureCrop(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return ageProperty(state)
                .map(property -> state.getValue(property).equals(property.getPossibleValues().stream()
                        .max(Integer::compareTo).orElse(0)))
                .orElse(false);
    }

    private static BlockState resetCropAge(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.getStateForAge(0);
        }
        return ageProperty(state)
                .map(property -> state.setValue(property, property.getPossibleValues().stream()
                        .min(Integer::compareTo).orElse(0)))
                .orElse(state);
    }

    private static java.util.Optional<IntegerProperty> ageProperty(BlockState state) {
        return state.getProperties().stream()
                .filter(IntegerProperty.class::isInstance)
                .map(IntegerProperty.class::cast)
                .filter(property -> property.getName().equals("age"))
                .findFirst();
    }

    private static int farmSizeTierFromDoubledScore(int score) {
        if (score >= LARGE_THRESHOLD * 2) {
            return 3;
        }
        if (score >= MEDIUM_THRESHOLD * 2) {
            return 2;
        }
        if (score >= SMALL_THRESHOLD * 2) {
            return 1;
        }
        return 0;
    }
}
