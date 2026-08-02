package dev.campaigncore.settlers.detection;

import dev.architectury.event.events.common.TickEvent;
import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.settlement.SettlementManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/// Discovers generated structures that may be treated as settlements.
///
/// Discovery and validation are separate stages:
///
/// 1. Structure tags, explicit configuration, and ID keywords establish candidacy.
/// 2. SettlementCandidateEvaluator checks whether the located structure contains
///    enough infrastructure to function as a settlement.
/// 3. SettlementConverter performs the actual conversion.
///
/// No world generation is modified. Structures generate normally and are interpreted
/// after generation when a player enters their structure start.
public final class VillageDetector {
    private static final int CHECK_INTERVAL_TICKS = 100;

    /// Structures in this tag are strong settlement candidates.
    ///
    /// The built-in tag may continue to include #minecraft:village, while datapacks
    /// and compatibility packs may add structures from other mods.
    public static final TagKey<Structure> CONVERTIBLE_VILLAGE_TAG =
            TagKey.create(
                    Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath(
                            SettlersMod.MOD_ID,
                            "convertible_village"
                    )
            );

    /// Structures in this tag are only candidates when they pass full
    /// infrastructure qualification.
    ///
    /// This is useful for camps, outposts, homesteads, crossroads, and similar
    /// structures that may or may not be large enough to support a settlement.
    public static final TagKey<Structure> CONDITIONAL_SETTLEMENT_TAG =
            TagKey.create(
                    Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath(
                            SettlersMod.MOD_ID,
                            "conditional_settlement"
                    )
            );

    /// Structures in this tag always become settlements when discovered.
    /// They bypass the conversion-chance roll and infrastructure qualification,
    /// as well as the master conversion toggle, while still requiring a surface start.
    public static final TagKey<Structure> GUARANTEED_SETTLEMENT_TAG =
            TagKey.create(
                    Registries.STRUCTURE,
                    ResourceLocation.fromNamespaceAndPath(
                            SettlersMod.MOD_ID,
                            "guaranteed_settlement"
                    )
            );

    private VillageDetector() {
    }

    public static void register() {
        TickEvent.SERVER_LEVEL_POST.register(VillageDetector::tick);
    }

    /// A start is considered underground (and thus ineligible) when its vertical centre sits more than
    /// this many blocks below the terrain surface at that column — comfortably clears cellars and
    /// hillside builds while excluding fully buried structures.
    private static final int SURFACE_TOLERANCE = 12;

    private static void tick(ServerLevel level) {
        SettlersConfig.Conversion config =
                SettlersConfig.get().conversion;

        // Settlements only form on the overworld surface: skip the Nether and the End wholesale.
        if (level.getGameTime() % CHECK_INTERVAL_TICKS != 0
                || level.players().isEmpty()
                || level.dimension() != Level.OVERWORLD) {
            return;
        }

        SettlementManager manager = SettlementManager.get(level);
        List<StructureCandidate> candidates = candidateStructures(level);

        for (ServerPlayer player : level.players()) {
            // Locate first (cheap), so the expensive infrastructure scan below only runs for a start
            // we're actually going to decide on this tick — never for one already tracked.
            findLocatedCandidate(level, player, candidates, candidate -> true)
                    .ifPresent(located -> {
                        StructureStart start = located.start();
                        var chunkPos = start.getChunkPos();
                        boolean guaranteed = located.candidate().strength()
                                == CandidateStrength.GUARANTEED;
                        // First-party authored settlements ship as settlements, rather than as
                        // structures optionally adapted by the conversion system. The conversion
                        // toggle therefore only controls ordinary and compatibility candidates.
                        if (!guaranteed && !config.enableSettlementConversion) {
                            return;
                        }
                        if (manager.isConverted(chunkPos)) {
                            return;
                        }
                        if (manager.isStartTracked(chunkPos)) {
                            if (!guaranteed) {
                                return;
                            }
                            // Worlds created before this structure became guaranteed may remember a
                            // failed roll. Allow those frontier hubs to self-heal on rediscovery.
                            manager.unrejectStart(chunkPos);
                        }

                        SurfaceVerdict surface = surfaceVerdict(level, start);
                        if (surface == SurfaceVerdict.UNDETERMINED) {
                            // Centre chunk not loaded yet — don't decide, retry when it is.
                            return;
                        }
                        if (surface == SurfaceVerdict.UNDERGROUND) {
                            manager.rejectStart(chunkPos);
                            SettlersMod.LOGGER.info(
                                    "Candidate at {} ({}) is underground; not eligible to become a settlement.",
                                    chunkPos, located.structureId());
                            return;
                        }

                        // Guaranteed first-party settlements bypass the probabilistic adapter used
                        // for ordinary villages and compatibility structures.
                        if (!guaranteed && !shouldConvert(level, start, config.conversionChance)) {
                            manager.rejectStart(chunkPos);
                            SettlersMod.LOGGER.info(
                                    "Settlement candidate at {} ({}) rolled against conversion; it remains "
                                            + "an ordinary structure.",
                                    chunkPos, located.structureId());
                            return;
                        }

                        SettlementQualification qualification =
                                SettlementCandidateEvaluator.evaluate(level, start, located.candidate());
                        if (!qualification.accepted()) {
                            // Track the rejection so the full-volume scan isn't repeated every interval
                            // while the player lingers in a structure that can't qualify.
                            manager.rejectStart(chunkPos);
                            SettlersMod.LOGGER.info(
                                    "Settlement candidate at {} ({}) failed infrastructure qualification: {}",
                                    chunkPos, located.structureId(), qualification.failures());
                            return;
                        }

                        SettlementConverter.convert(level, start, located.villageType());
                    });
        }
    }

    private enum SurfaceVerdict {
        SURFACE,
        UNDERGROUND,
        UNDETERMINED
    }

    /// Whether a structure start sits on the surface, is buried, or can't yet be told (centre chunk not
    /// loaded). Compares the start's vertical centre against the terrain surface height at its centre
    /// column — see {@link #SURFACE_TOLERANCE}.
    private static SurfaceVerdict surfaceVerdict(ServerLevel level, StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        int cx = (box.minX() + box.maxX()) / 2;
        int cz = (box.minZ() + box.maxZ()) / 2;
        if (!level.hasChunk(cx >> 4, cz >> 4)) {
            return SurfaceVerdict.UNDETERMINED;
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, cx, cz);
        int centerY = (box.minY() + box.maxY()) / 2;
        return centerY >= surfaceY - SURFACE_TOLERANCE
                ? SurfaceVerdict.SURFACE
                : SurfaceVerdict.UNDERGROUND;
    }

    private static final long CONVERSION_CHANCE_SALT = 0x5E771E4C9A31B2D7L;

    private static boolean shouldConvert(
            ServerLevel level,
            StructureStart start,
            double conversionChance
    ) {
        long seed = level.getSeed();
        long positionSeed = start.getChunkPos().toLong();

        long mixedSeed = mixSeed(
                seed
                        ^ positionSeed
                        ^ CONVERSION_CHANCE_SALT
        );

        RandomSource random = RandomSource.create(mixedSeed);
        return random.nextDouble() < conversionChance;
    }

    private static long mixSeed(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdl;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53l;
        value ^= value >>> 33;
        return value;
    }
    /// Builds the complete candidate list for this level.
    ///
    /// Precedence:
    ///
    /// explicit exclusion
    /// explicit inclusion
    /// keyword exclusion
    /// convertible tag / strong keyword
    /// conditional tag / conditional keyword
    /// guaranteed-settlement tag (final, authoritative override)
    ///
    /// A structure discovered through more than one mechanism is retained once,
    /// using its strongest discovery classification.
    private static List<StructureCandidate> candidateStructures(
            ServerLevel level
    ) {
        Registry<Structure> registry =
                level.registryAccess()
                        .registryOrThrow(Registries.STRUCTURE);

        SettlersConfig.Conversion config =
                SettlersConfig.get().conversion;

        Map<ResourceLocation, StructureCandidate> candidates =
                new LinkedHashMap<>();

        /*
         * Explicit includes are processed while iterating the registry below,
         * because configuration entries are substring patterns rather than
         * necessarily exact registry IDs.
         */

        registry.getTag(CONVERTIBLE_VILLAGE_TAG)
                .ifPresent(tagged -> tagged.forEach(holder -> {
                    Optional<ResourceLocation> optionalId =
                            structureId(holder);

                    if (optionalId.isEmpty()) {
                        return;
                    }

                    String normalizedId =
                            normalize(optionalId.get());

                    if (matchesAny(
                            normalizedId,
                            config.explicitStructureExcludes
                    )) {
                        return;
                    }

                    addCandidate(
                            candidates,
                            holder,
                            CandidateStrength.STRONG,
                            "settlers:convertible_village tag"
                    );
                }));

        registry.getTag(CONDITIONAL_SETTLEMENT_TAG)
                .ifPresent(tagged -> tagged.forEach(holder -> {
                    Optional<ResourceLocation> optionalId =
                            structureId(holder);

                    if (optionalId.isEmpty()) {
                        return;
                    }

                    String normalizedId =
                            normalize(optionalId.get());

                    if (matchesAny(
                            normalizedId,
                            config.explicitStructureExcludes
                    )) {
                        return;
                    }

                    addCandidate(
                            candidates,
                            holder,
                            CandidateStrength.CONDITIONAL,
                            "settlers:conditional_settlement tag"
                    );
                }));

        registry.holders().forEach(holder ->
                holder.unwrapKey().ifPresent(key -> {
                    ResourceLocation id = key.location();
                    String normalizedId = normalize(id);

                    /*
                     * Explicit exclusions have absolute priority among ordinary
                     * conversion candidates. Guaranteed authored settlements are
                     * applied after this configuration pass.
                     */
                    if (matchesAny(
                            normalizedId,
                            config.explicitStructureExcludes
                    )) {
                        candidates.remove(id);
                        return;
                    }

                    /*
                     * An explicit include bypasses ordinary keyword
                     * classification and receives the strongest candidacy.
                     */
                    if (matchesAny(
                            normalizedId,
                            config.explicitStructureIncludes
                    )) {
                        addCandidate(
                                candidates,
                                holder,
                                CandidateStrength.EXPLICIT,
                                "explicit configuration include"
                        );
                        return;
                    }

                    /*
                     * Ordinary exclusions do not override an explicit include,
                     * but do remove tag-discovered candidates.
                     */
                    if (matchesAny(
                            normalizedId,
                            config.excludedStructureKeywords
                    )) {
                        candidates.remove(id);
                        return;
                    }

                    if (matchesAny(
                            normalizedId,
                            config.includedStructureKeywords
                    )) {
                        addCandidate(
                                candidates,
                                holder,
                                CandidateStrength.STRONG,
                                "strong structure-ID keyword"
                        );
                        return;
                    }

                    if (matchesAny(
                            normalizedId,
                            config.conditionalStructureKeywords
                    )) {
                        addCandidate(
                                candidates,
                                holder,
                                CandidateStrength.CONDITIONAL,
                                "conditional structure-ID keyword"
                        );
                    }
                }));

        /*
         * Guaranteed structures are applied last and cannot be removed by the
         * ordinary include/exclude filters above. This tag is reserved for
         * structures authored specifically to begin life as settlements.
         */
        registry.getTag(GUARANTEED_SETTLEMENT_TAG)
                .ifPresent(tagged -> tagged.forEach(holder ->
                        addCandidate(
                                candidates,
                                holder,
                                CandidateStrength.GUARANTEED,
                                "settlers:guaranteed_settlement tag"
                        )
                ));

        return List.copyOf(candidates.values());
    }

    /// Finds any recognized candidate structure at the player's position,
    /// without requiring the candidate to pass infrastructure qualification.
    ///
    /// This preserves the diagnostic distinction between:
    ///
    /// - no settlement-like structure is present;
    /// - a candidate is present but fails qualification.
    public static Optional<LocatedCandidate> findVillageStartAt(
            ServerLevel level,
            ServerPlayer player
    ) {
        return findLocatedCandidate(
                level,
                player,
                candidateStructures(level),
                candidate -> true
        );
    }

    /// Evaluates the first candidate structure found at the player's position,
    /// including failed qualification results. This is useful for a
    /// /settlers diagnose command.
    public static Optional<CandidateDiagnostic> diagnoseAt(
            ServerLevel level,
            ServerPlayer player
    ) {
        return findVillageStartAt(level, player)
                .map(located -> {
                    SettlementQualification qualification =
                            SettlementCandidateEvaluator.evaluate(
                                    level,
                                    located.start(),
                                    located.candidate()
                            );

                    return new CandidateDiagnostic(
                            located.start(),
                            located.candidate(),
                            qualification
                    );
                });
    }

    /// Internal location lookup. This method only determines whether the
    /// player is inside a candidate structure start. It does not perform
    /// infrastructure qualification.
    private static Optional<LocatedCandidate> findLocatedCandidate(
            ServerLevel level,
            ServerPlayer player,
            List<StructureCandidate> candidates,
            Predicate<StructureCandidate> candidateFilter
    ) {
        BlockPos pos = player.blockPosition();

        /*
         * Query the structures present at this position once, then intersect
         * that result with the candidate list. This avoids one blind structure
         * lookup for every registered candidate.
         */
        var present =
                level.structureManager().getAllStructuresAt(pos);

        if (present.isEmpty()) {
            return Optional.empty();
        }

        for (StructureCandidate candidate : candidates) {
            if (!candidateFilter.test(candidate)) {
                continue;
            }

            Structure structure = candidate.holder().value();

            if (!present.containsKey(structure)) {
                continue;
            }

            StructureStart start =
                    level.structureManager()
                            .getStructureAt(pos, structure);

            if (!start.isValid()) {
                continue;
            }

            return Optional.of(
                    new LocatedCandidate(start, candidate)
            );
        }

        return Optional.empty();
    }

    /// Adds or upgrades a structure candidate.
    ///
    /// The same structure can be discovered through multiple mechanisms:
    /// a structure tag, a keyword, and an explicit include. The map retains
    /// one entry and preserves the strongest reason.
    private static void addCandidate(
            Map<ResourceLocation, StructureCandidate> candidates,
            Holder<Structure> holder,
            CandidateStrength strength,
            String reason
    ) {
        holder.unwrapKey().ifPresent(key -> {
            ResourceLocation id = key.location();

            StructureCandidate incoming =
                    new StructureCandidate(
                            holder,
                            id,
                            strength,
                            reason
                    );

            candidates.merge(
                    id,
                    incoming,
                    (existing, replacement) -> {
                        if (replacement.strength().priority()
                                > existing.strength().priority()) {
                            return replacement;
                        }

                        return existing;
                    }
            );
        });
    }

    private static Optional<ResourceLocation> structureId(
            Holder<Structure> holder
    ) {
        return holder.unwrapKey().map(key -> key.location());
    }

    /// Matches normalized registry IDs against normalized substring patterns.
    ///
    /// Blank and null patterns are ignored. A literal "*" matches everything.
    private static boolean matchesAny(
            String normalizedId,
            List<String> patterns
    ) {
        if (patterns == null || patterns.isEmpty()) {
            return false;
        }

        return patterns.stream()
                .filter(pattern ->
                        pattern != null && !pattern.isBlank()
                )
                .map(pattern ->
                        pattern.toLowerCase(Locale.ROOT).trim()
                )
                .anyMatch(pattern ->
                        pattern.equals("*")
                                || normalizedId.contains(pattern)
                );
    }

    private static String normalize(ResourceLocation id) {
        return id.toString().toLowerCase(Locale.ROOT);
    }

    /// Strength of the evidence that a structure should be considered for
    /// settlement conversion.
    public enum CandidateStrength {
        /// Authored specifically as a settlement; bypasses chance and qualification.
        GUARANTEED(4),

        /// Explicitly enabled through configuration. The evaluator may permit
        /// this candidate without normal infrastructure requirements.
        EXPLICIT(3),

        /// Village tag member or strongly settlement-like structure ID.
        STRONG(2),

        /// Ambiguous structure such as a camp, outpost, or homestead.
        CONDITIONAL(1);

        private final int priority;

        CandidateStrength(int priority) {
            this.priority = priority;
        }

        public int priority() {
            return this.priority;
        }
    }

    /// A registered structure that may be eligible for conversion.
    public record StructureCandidate(
            Holder<Structure> holder,
            ResourceLocation id,
            CandidateStrength strength,
            String reason
    ) {
    }

    /// A candidate structure start containing the player's current position.
    ///
    /// This record does not imply that qualification passed.
    public record LocatedCandidate(
            StructureStart start,
            StructureCandidate candidate
    ) {
        public ResourceLocation structureId() {
            return this.candidate.id();
        }

        public String villageType() {
            return this.candidate.id().getNamespace();
        }
    }

    /// Complete diagnostic result, including candidates that failed
    /// qualification.
    public record CandidateDiagnostic(
            StructureStart start,
            StructureCandidate candidate,
            SettlementQualification qualification
    ) {
        public ResourceLocation structureId() {
            return this.candidate.id();
        }

        public String villageType() {
            return this.candidate.id().getNamespace();
        }

        public boolean accepted() {
            return this.qualification.accepted();
        }
    }
}
