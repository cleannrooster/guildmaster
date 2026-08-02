package dev.campaigncore.settlers.detection;

import dev.campaigncore.settlers.config.SettlersConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;

/// Determines whether a discovered structure candidate contains enough
/// infrastructure to function as a settlement.
///
/// This class is read-only. It does not create anchors, classify final
/// structure roles, spawn settlers, or modify the world.
public final class SettlementCandidateEvaluator {
    private SettlementCandidateEvaluator() {
    }

    public static SettlementQualification evaluate(
            ServerLevel level,
            StructureStart start,
            VillageDetector.StructureCandidate candidate
    ) {
        SettlersConfig.Conversion config =
                SettlersConfig.get().conversion;

        InfrastructureScan scan =
                InfrastructureScanner.scan(level, start);

        /*
         * Explicit includes are treated as authoritative overrides.
         *
         * The scan is still retained so diagnostics can report what the
         * structure contains, even though normal requirements are bypassed.
         */
        if (candidate.strength()
                == VillageDetector.CandidateStrength.GUARANTEED) {
            return SettlementQualification.accepted(
                    "Guaranteed settlement structure.",
                    scan
            );
        }

        if (candidate.strength()
                == VillageDetector.CandidateStrength.EXPLICIT) {
            return SettlementQualification.accepted(
                    "Explicitly included by configuration.",
                    scan
            );
        }
        List<String> failures = new ArrayList<>();

        if (!config.requireInfrastructureQualification) {
            return SettlementQualification.accepted(
                    "Infrastructure qualification is disabled.",
                    scan
            );
        }


        int requiredPieces = config.minimumStructurePieces;
        int requiredBeds = config.minimumBeds;
        int requiredWorkstations = config.minimumWorkAnchors;

        /*
         * Strong candidates have already provided good evidence through a
         * structure tag or strongly settlement-like registry ID.
         *
         * They can use slightly relaxed minimums while conditional candidates
         * such as camps and outposts must meet the complete requirements.
         */
        if (candidate.strength()
                == VillageDetector.CandidateStrength.STRONG) {
            requiredPieces = Math.min(requiredPieces, 2);
            requiredBeds = Math.min(requiredBeds, 1);
        }

        if (scan.structurePieces() < requiredPieces) {
            failures.add(
                    "Structure pieces: "
                            + scan.structurePieces()
                            + " / "
                            + requiredPieces
            );
        }

        if (scan.beds() < requiredBeds) {
            failures.add(
                    "Beds: "
                            + scan.beds()
                            + " / "
                            + requiredBeds
            );
        }

        if (config.requireFarmInfrastructure
                && !scan.hasFoodInfrastructure()) {
            failures.add("No farm infrastructure detected.");
        }

        if (scan.workstations() < requiredWorkstations) {
            failures.add(
                    "Workstations: "
                            + scan.workstations()
                            + " / "
                            + requiredWorkstations
            );
        }

        /*
         * The settlement must be able to house and feed a minimum permanent
         * population to be worth converting. Estimated from aggregate scan
         * counts; the infirmary modifier is unknown at this stage, so this is
         * a conservative (never over-stated) estimate — the authoritative cap
         * is recomputed per-structure in SettlementConverter.
         */
        int farmTiers = FarmSizeClassifier.farmTier(scan.farmland(), scan.plantedCrops());
        int supportable =
                FarmSizeClassifier.supportablePopulation(scan.beds(), farmTiers, 0.0);

        if (supportable < config.minimumSupportedPopulation) {
            failures.add(
                    "Supported population: "
                            + supportable
                            + " / "
                            + config.minimumSupportedPopulation
            );
        }

        if (!failures.isEmpty()) {
            return SettlementQualification.rejected(
                    "Candidate lacks sufficient settlement infrastructure.",
                    scan,
                    failures
            );
        }

        return SettlementQualification.accepted(
                "Candidate passed infrastructure qualification.",
                scan
        );
    }
}
