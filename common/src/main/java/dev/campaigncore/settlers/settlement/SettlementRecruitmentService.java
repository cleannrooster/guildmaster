package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.entity.data.SettlerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/// Authoritative housing, vacancy, and employment rules shared by commands and future gameplay.
public final class SettlementRecruitmentService {
    public static final ResourceLocation CIVILIAN = id("civilian");
    private static final List<ResourceLocation> JOB_PRIORITY = List.of(
            id("farmer"), id("herder"), id("fisher"), id("smith"), id("shrine_keeper"));

    private SettlementRecruitmentService() {
    }

    public static RecruitmentResult recruit(ServerLevel level, Settlement settlement, ResourceLocation profileId) {
        if (!SettlersConfig.get().population.generateSettlers) {
            return RecruitmentResult.failure("Settler generation is disabled in the server configuration.");
        }
        Optional<SettlerProfile> profile = SettlerDataManager.profile(profileId);
        if (profile.isEmpty()) {
            return RecruitmentResult.failure("Unknown settler profile: " + profileId);
        }
        if (settlement.threatState() != ThreatState.NORMAL) {
            return RecruitmentResult.failure("The settlement is not peaceful enough to recruit.");
        }
        Optional<BlockPos> bed = unusedBed(settlement);
        if (bed.isEmpty()) {
            return RecruitmentResult.failure("No unoccupied bed is available.");
        }
        Optional<BlockPos> home = nearestHome(settlement, bed.get());
        if (home.isEmpty()) {
            return RecruitmentResult.failure("No home is available for that bed.");
        }

        ResidentEntry entry = new ResidentEntry(profileId);
        entry.setHome(home.get());
        entry.setSleep(bed.get());
        BlockPos work = null;
        if (!profileId.equals(CIVILIAN)) {
            Optional<WorkAssignment> assignment = availableAssignment(settlement, profileId);
            if (assignment.isEmpty()) {
                return RecruitmentResult.failure("No vacant " + profile.get().profession() + " position is available.");
            }
            entry.setWork(assignment.get().type(), assignment.get().position());
            work = assignment.get().position();
        }
        settlement.roster().add(entry);
        settlement.recordChronicle(level.getGameTime(), "resident_joined", profile.get().profession());
        SettlementManager.get(level).markDirty();
        SettlerPopulator.populate(level, settlement);
        return RecruitmentResult.success(entry, home.get(), work);
    }

    public static boolean hasAvailableHousing(Settlement settlement) {
        return unusedBed(settlement).isPresent()
                && !settlement.anchors().all(AnchorType.HOME).isEmpty();
    }

    public static RecruitmentResult settleTraveler(ServerLevel level, Settlement settlement, SettlerEntity traveler) {
        if (settlement.threatState() != ThreatState.NORMAL) {
            return RecruitmentResult.failure("The settlement is not peaceful enough to accept a traveler.");
        }
        Optional<BlockPos> bed = unusedBed(settlement);
        if (bed.isEmpty()) {
            return RecruitmentResult.failure("No unoccupied bed is available.");
        }
        Optional<BlockPos> home = nearestHome(settlement, bed.get());
        if (home.isEmpty()) {
            return RecruitmentResult.failure("No home is available for that bed.");
        }
        ResidentEntry entry = new ResidentEntry(CIVILIAN);
        entry.setHome(home.get());
        entry.setSleep(bed.get());
        entry.bind(traveler.getUUID());
        settlement.roster().add(entry);
        settlement.recordChronicle(level.getGameTime(), "traveler_stayed", "civilian");
        traveler.applyProfile(CIVILIAN);
        traveler.bindSettlement(settlement.id());
        traveler.setHomeAnchor(home.get());
        traveler.setSleepAnchor(bed.get());
        traveler.setDespawnTimer(-1);
        SettlementManager.get(level).markDirty();
        return RecruitmentResult.success(entry, home.get(), null);
    }

    public static void tickEmployment(ServerLevel level, Settlement settlement) {
        if (settlement.threatState() != ThreatState.NORMAL) {
            return;
        }
        for (ResidentEntry entry : settlement.roster()) {
            if (!entry.profile().equals(CIVILIAN) || entry.work().isPresent() || entry.jobOffer().isPresent()) {
                continue;
            }
            for (ResourceLocation job : JOB_PRIORITY) {
                Optional<WorkAssignment> assignment = availableAssignment(settlement, job);
                if (assignment.isEmpty()) {
                    continue;
                }
                entry.setWork(assignment.get().type(), assignment.get().position());
                entry.setJobOffer(job);
                entry.entityId().map(level::getEntity).filter(SettlerEntity.class::isInstance)
                        .map(SettlerEntity.class::cast)
                        .ifPresent(entity -> entity.setWorkAnchor(assignment.get().type(), assignment.get().position()));
                SettlementManager.get(level).markDirty();
                return;
            }
        }
    }

    public static boolean hasJobOffer(Settlement settlement, java.util.UUID entityId) {
        return settlement.roster().stream().anyMatch(entry -> entry.entityId().filter(entityId::equals).isPresent()
                && entry.jobOffer().isPresent());
    }

    public static boolean completeJobOffer(ServerLevel level, Settlement settlement, java.util.UUID entityId) {
        Optional<ResidentEntry> found = settlement.roster().stream()
                .filter(entry -> entry.entityId().filter(entityId::equals).isPresent())
                .filter(entry -> entry.jobOffer().isPresent())
                .findFirst();
        if (found.isEmpty()) {
            return false;
        }
        ResidentEntry entry = found.get();
        ResourceLocation profile = entry.jobOffer().orElseThrow();
        entry.setProfile(profile);
        entry.clearJobOffer();
        if (level.getEntity(entityId) instanceof SettlerEntity settler) {
            settler.applyProfile(profile);
        }
        SettlementManager.get(level).markDirty();
        return true;
    }

    private static Optional<BlockPos> unusedBed(Settlement settlement) {
        Set<BlockPos> used = settlement.roster().stream().flatMap(entry -> entry.sleep().stream())
                .collect(Collectors.toSet());
        return settlement.anchors().all(AnchorType.SLEEP).stream().filter(pos -> !used.contains(pos)).findFirst();
    }

    private static Optional<BlockPos> nearestHome(Settlement settlement, BlockPos bed) {
        return settlement.anchors().all(AnchorType.HOME).stream()
                .min(Comparator.comparingDouble(pos -> pos.distSqr(bed)));
    }

    private static Optional<WorkAssignment> availableAssignment(Settlement settlement, ResourceLocation profileId) {
        Optional<SettlerProfile> profile = SettlerDataManager.profile(profileId);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        String profession = profile.get().profession();
        int required = SettlementLaborModel.requiredWorkers(settlement, profession);
        long committed = settlement.roster().stream()
                .filter(entry -> SettlementLaborModel.professionOf(entry).equals(profession)
                        || entry.jobOffer().filter(profileId::equals).isPresent())
                .count();
        if (required <= committed) {
            return Optional.empty();
        }
        Set<BlockPos> occupied = settlement.roster().stream().flatMap(entry -> entry.work().stream())
                .collect(Collectors.toSet());
        for (AnchorType type : profile.get().resolveWorkstation()) {
            Optional<BlockPos> position = settlement.anchors().all(type).stream()
                    .filter(pos -> !occupied.contains(pos)).findFirst();
            if (position.isPresent()) {
                return Optional.of(new WorkAssignment(type, position.get()));
            }
        }
        return Optional.empty();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SettlersMod.MOD_ID, path);
    }

    private record WorkAssignment(AnchorType type, BlockPos position) {
    }
}
