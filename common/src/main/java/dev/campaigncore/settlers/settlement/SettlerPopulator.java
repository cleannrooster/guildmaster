package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.compat.FrontierArmaments;
import dev.campaigncore.settlers.config.SettlersConfig;
import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerDataManager;
import dev.campaigncore.settlers.entity.data.SettlerProfile;
import dev.campaigncore.settlers.registry.SettlersEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

/// Spawns a settlement's roster into the world and binds home/work/sleep anchors. Re-entrant and
/// self-healing: called repeatedly (once at conversion, then again on every SettlementTicker pass —
/// see that class), it never duplicates residents, and it retrofits anchor assignments onto
/// already-spawned residents too (e.g. residents populated by an older version of this class that
/// didn't assign a given anchor type yet, or that used per-settler random draws instead of
/// round-robin — the fix for those defects otherwise never reaches settlers already on the roster).
public final class SettlerPopulator {
    private SettlerPopulator() {
    }

    public static int populate(ServerLevel level, Settlement settlement) {
        if (!SettlersConfig.get().population.generateSettlers) {
            return 0;
        }
        RandomSource random = level.getRandom();
        int spawned = 0;
        boolean changed = false;
        List<BlockPos> homes = settlement.anchors().all(AnchorType.HOME);
        List<BlockPos> beds = settlement.anchors().all(AnchorType.SLEEP);
        int homeIndex = 0;
        int bedIndex = 0;
        Set<BlockPos> occupiedBeds = new HashSet<>();
        Set<BlockPos> occupiedWork = new HashSet<>();
        settlement.roster().forEach(entry -> {
            entry.sleep().ifPresent(occupiedBeds::add);
            entry.work().ifPresent(occupiedWork::add);
        });
        // One round-robin cursor per anchor type, shared across every profession that resolves to that
        // type (e.g. a farmer and a herder never both land on CROP_TENDING, but two farmers round-robin
        // across the settlement's composters/farmland the same way homes/beds already do).
        Map<AnchorType, Integer> workCursors = new HashMap<>();

        for (ResidentEntry entry : settlement.roster()) {
            // Round-robin every anchor type, like homes always were: an independent random draw per
            // settler lets several residents land on the exact same bed/workstation (worse, distinct
            // RandomSource instances created back-to-back in a tight loop can draw correlated values),
            // which is exactly the "everyone sleeps/works in one spot" symptom this avoids.
            if (entry.home().isEmpty() && !homes.isEmpty()) {
                entry.setHome(homes.get(homeIndex++ % homes.size()));
                changed = true;
            }
            if (entry.work().isEmpty() && assignWorkAnchor(entry, settlement, workCursors, occupiedWork)) {
                changed = true;
            } else if (entry.work().isPresent() && entry.workType().isEmpty() && retrofitWorkType(entry, settlement)) {
                // Old saves persisted a work position but no work type (the type field is newer);
                // recover it from the profile's preference order so production can route the resident.
                changed = true;
            }
            if (entry.sleep().isEmpty() && !beds.isEmpty()) {
                while (bedIndex < beds.size() && occupiedBeds.contains(beds.get(bedIndex))) {
                    bedIndex++;
                }
                if (bedIndex < beds.size()) {
                    BlockPos bed = beds.get(bedIndex++);
                    entry.setSleep(bed);
                    occupiedBeds.add(bed);
                    changed = true;
                }
            }

            Entity resolved = entry.entityId().map(level::getEntity).orElse(null);
            if (resolved != null && (!(resolved instanceof SettlerEntity settler)
                    || !settler.isAlive() || settler.isRemoved())) {
                // A dying entity remains addressable by UUID briefly. Do not mistake that corpse for
                // a healthy resident and suppress its replacement during an immediate /repopulate.
                entry.unbind();
                changed = true;
                resolved = null;
            }
            Optional<SettlerEntity> existing = Optional.ofNullable(resolved)
                    .filter(SettlerEntity.class::isInstance)
                    .map(SettlerEntity.class::cast);
            if (entry.entityId().isPresent() && existing.isEmpty()) {
                // Entity id recorded but its chunk isn't currently loaded — leave it alone rather than
                // risk spawning a duplicate; anchors above are still fixed on the roster entry, so the
                // live entity will pick them up next time it's actually loaded and this runs again.
                // (Death is not this case: a dying settler unbinds its own roster entry — see
                // SettlerEntity.die — so an id that no longer resolves really is just an unloaded chunk.)
                continue;
            }
            if (existing.isPresent()) {
                // Already spawned: just push any newly-assigned anchors onto it (the retrofit path).
                SettlerEntity settler = existing.get();
                entry.home().ifPresent(settler::setHomeAnchor);
                pushWorkAnchor(entry, settler);
                entry.sleep().ifPresent(settler::setSleepAnchor);
                themeReeveIfNeeded(settler, entry, settlement);
                equipGuardIfNeeded(settler, entry, settlement);
                continue;
            }
            if (settlement.threatState() != ThreatState.NORMAL) {
                // No fresh spawns (initial or replacement) while things are unsettled: a raid that kills
                // a settler must not conjure a same-second replacement into the same fight. Recruits
                // arrive once the settlement has returned to NORMAL.
                continue;
            }

            BlockPos pos = entry.home().orElse(settlement.center());

            SettlerEntity settler = SettlersEntities.SETTLER.get().create(level);
            if (settler == null) {
                continue;
            }
            settler.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360.0f, 0.0f);
            settler.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
            settler.applyProfile(entry.profile());
            if (SettlerDataManager.profile(entry.profile()).isEmpty()) {
                SettlersMod.LOGGER.warn("Roster entry references missing profile {}; settler spawned with generic fallback.",
                        entry.profile());
            }
            settler.bindSettlement(settlement.id());
            entry.home().ifPresent(settler::setHomeAnchor);
            pushWorkAnchor(entry, settler);
            entry.sleep().ifPresent(settler::setSleepAnchor);
            themeReeveIfNeeded(settler, entry, settlement);
            equipGuardIfNeeded(settler, entry, settlement);

            level.addFreshEntity(settler);
            entry.bind(settler.getUUID());
            spawned++;
        }

        if (changed || spawned > 0) {
            // Only dirty the SavedData when this pass actually assigned or spawned something —
            // unconditional dirtying kept it permanently dirty, re-encoding every settlement on every
            // autosave forever.
            SettlementManager.get(level).markDirty();
        }
        if (spawned > 0) {
            SettlersMod.LOGGER.info("Populated settlement '{}' with {} new settlers ({} roster entries already bound).",
                    settlement.name(), spawned, settlement.roster().size() - spawned);
        }
        return spawned;
    }

    /// Routes a resident to a workstation anchor matching its profession (composter/farmland for a
    /// farmer, a barrel for a fisher, a smithing table for a smith, ...) instead of the single generic
    /// WORK bucket every profession used to share. Walks the profile's resolved anchor-type preference
    /// list and takes the first type the settlement actually has any of, round-robining within it;
    /// falls back to WORK, then leaves the entry unassigned (WorkBehavior falls back further still).
    /// Returns whether an anchor was assigned.
    private static boolean assignWorkAnchor(ResidentEntry entry, Settlement settlement,
                                            Map<AnchorType, Integer> cursors, Set<BlockPos> occupied) {
        if (entry.profile().equals(SettlementRecruitmentService.CIVILIAN)) {
            return false;
        }
        List<AnchorType> preference = SettlerDataManager.profile(entry.profile())
                .map(SettlerProfile::resolveWorkstation)
                .orElse(List.of(AnchorType.WORK));
        for (AnchorType type : preference) {
            List<BlockPos> spots = settlement.anchors().all(type);
            if (spots.isEmpty()) {
                continue;
            }
            for (BlockPos spot : spots) {
                if (occupied.add(spot)) {
                    entry.setWork(type, spot);
                    cursors.merge(type, 1, Integer::sum);
                    return true;
                }
            }
        }
        return false;
    }

    /// Recovers the work anchor *type* for a resident that already has a work *position* from an older
    /// save (the type field postdates the position). Walks the profile's preference order and prefers
    /// the first type whose anchor list actually contains the assigned position; otherwise the first
    /// preferred type the settlement has any of; ultimately {@link AnchorType#WORK}. Returns whether a
    /// type was assigned (always true when a work position is present). Never moves the position.
    private static boolean retrofitWorkType(ResidentEntry entry, Settlement settlement) {
        Optional<BlockPos> workPos = entry.work();
        if (workPos.isEmpty()) {
            return false;
        }
        List<AnchorType> preference = SettlerDataManager.profile(entry.profile())
                .map(SettlerProfile::resolveWorkstation)
                .orElse(List.of(AnchorType.WORK));
        AnchorType resolved = null;
        for (AnchorType type : preference) {
            List<BlockPos> spots = settlement.anchors().all(type);
            if (spots.contains(workPos.get())) {
                resolved = type;
                break;
            }
            if (resolved == null && !spots.isEmpty()) {
                // Remember the first preferred type the settlement has any of, as a fallback if no
                // exact position match is found further down the preference list.
                resolved = type;
            }
        }
        if (resolved == null) {
            resolved = AnchorType.WORK;
        }
        entry.setWork(resolved, workPos.get());
        return true;
    }

    /// Pushes a resident's persisted work anchor (type + position) onto its live entity. Falls back to
    /// {@link AnchorType#WORK} if the type is somehow absent, so the entity always gets a coherent pair.
    private static void pushWorkAnchor(ResidentEntry entry, SettlerEntity settler) {
        entry.work().ifPresent(pos -> settler.setWorkAnchor(entry.workType().orElse(AnchorType.WORK), pos));
    }

    /// Applies the settlement's theme (title + armor) to its reeve, once. Gated on the reeve not yet
    /// having a custom name so it runs a single time per reeve — on first spawn, or on the first
    /// population pass over a settlement that predates themed reeves — and self-heals older saves.
    private static void themeReeveIfNeeded(SettlerEntity settler, ResidentEntry entry, Settlement settlement) {
        if (settler.getCustomName() == null && SettlementLaborModel.professionOf(entry).equals("reeve")) {
            ReeveTheme.apply(settler, settlement);
        }
    }

    /// When the optional Frontier Armaments mod is installed, dresses a guard in the armor set matching
    /// the settlement's theme (so guards match their reeve). No-op without the mod, or if the guard is
    /// already wearing that gear — so it runs once and self-heals settlements that predate the mod being
    /// added. Guards without the mod keep their vanilla datapack equipment.
    private static void equipGuardIfNeeded(SettlerEntity settler, ResidentEntry entry, Settlement settlement) {
        if (!FrontierArmaments.isPresent() || FrontierArmaments.alreadyEquipped(settler)) {
            return;
        }
        if (SettlementLaborModel.professionOf(entry).equals("militia")) {
            FrontierArmaments.equipGuard(settler, ReeveTheme.identify(settlement).theme());
        }
    }
}
