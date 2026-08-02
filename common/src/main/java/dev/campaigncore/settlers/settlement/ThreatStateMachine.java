package dev.campaigncore.settlers.settlement;

import dev.campaigncore.settlers.SettlersMod;
import dev.campaigncore.settlers.entity.SettlerEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;

import static dev.campaigncore.settlers.entity.data.SettlerRole.GUARD;

/// Drives one settlement's threat state from a throttled hostile-mob census.
///
/// State semantics:
/// - NORMAL: no relevant hostile activity.
/// - ALERT: a hostile is near the settlement, but no attack is confirmed.
/// - UNDER_ATTACK: a hostile is targeting a resident, or a guard has confirmed an intrusion.
/// - PANIC: an attack is confirmed, but no living guards remain.
/// - RECOVERY: the confirmed threat has ended; residents remain sheltered briefly.
/// - ABANDONED: threat logic does not modify the settlement.
public final class ThreatStateMachine {
    private static final double ALERT_BUFFER = 24.0;

    private static final double SCAN_BELOW_CENTER = 4.0;
    private static final double SCAN_ABOVE_CENTER = 8.0;

    private static final double GUARD_CONFIRM_RANGE = 12.0;
    private static final double GUARD_CONFIRM_RANGE_SQR =
            GUARD_CONFIRM_RANGE * GUARD_CONFIRM_RANGE;

    private static final int RECOVERY_DURATION_TICKS = 100;
    /// Once an attack is confirmed, UNDER_ATTACK/PANIC persists for at least twenty seconds.
    private static final int MINIMUM_ATTACK_STATE_TICKS = 400;

    private ThreatStateMachine() {
    }

    public static void tick(ServerLevel level, Settlement settlement) {
        AABB settlementBounds = AABB.of(settlement.bounds());
        int centerY = settlement.center().getY();

        /*
         * Deliberately limit the vertical scan around the settlement's surface.
         * Inflating the original AABB vertically can include cave mobs beneath town.
         */
        AABB scanArea = new AABB(
                settlementBounds.minX - ALERT_BUFFER,
                centerY - SCAN_BELOW_CENTER,
                settlementBounds.minZ - ALERT_BUFFER,
                settlementBounds.maxX + ALERT_BUFFER,
                centerY + SCAN_ABOVE_CENTER,
                settlementBounds.maxZ + ALERT_BUFFER
        );

        List<Monster> hostiles = level.getEntitiesOfClass(
                Monster.class,
                scanArea,
                ThreatStateMachine::isRelevantHostile
        );

        List<SettlerEntity> guards = settlement.roster().stream()
                .map(entry -> entry.entityId()
                        .map(level::getEntity)
                        .orElse(null))
                .filter(SettlerEntity.class::isInstance)
                .map(SettlerEntity.class::cast)
                .filter(SettlerEntity::isAlive)
                .filter(settler -> settler.role() == GUARD)
                .toList();

        boolean hasAnyGuards = !guards.isEmpty();
        boolean hostilesNearby = !hostiles.isEmpty();

        /*
         * Physical presence inside the settlement is an intrusion signal, but does
         * not by itself confirm an attack. This avoids permanent emergencies caused
         * by trapped, unaware, or inaccessible monsters.
         */
        boolean hostilesInside = hostiles.stream().anyMatch(
                hostile -> settlement.contains(hostile.blockPosition())
        );

        /*
         * This is the strongest confirmation signal: a hostile has actually selected
         * a resident of this settlement as its combat target.
         */
        boolean hostileTargetingResident = hostiles.stream().anyMatch(hostile -> {
            if (!(hostile.getTarget() instanceof SettlerEntity target)) {
                return false;
            }

            return target.isAlive()
                    && settlement.contains(target.blockPosition());
        });

        /*
         * Guards confirm an attack only when:
         * - the hostile is close enough,
         * - the guard has line of sight,
         * - and the hostile is either inside the settlement or targeting a resident.
         *
         * Merely seeing a monster outside town is only an ALERT condition.
         */
        boolean guardConfirmedAttack = guards.stream().anyMatch(guard ->
                hostiles.stream().anyMatch(hostile ->
                        guard.distanceToSqr(hostile) <= GUARD_CONFIRM_RANGE_SQR
                                && guard.getSensing().hasLineOfSight(hostile)
                                && (
                                settlement.contains(hostile.blockPosition())
                                        || hostile.getTarget() instanceof SettlerEntity
                        )
                )
        );

        boolean confirmedAttack =
                hostileTargetingResident
                        || guardConfirmedAttack;

        /*
         * Any nearby monster or geometric intrusion is sufficient for ALERT.
         * Civilians do not shelter during ALERT; guards investigate instead.
         */
        boolean earlyWarning =
                hostilesNearby
                        || hostilesInside;

        ThreatState current = settlement.threatState();
        ThreatState next = current;

        if ((current == ThreatState.UNDER_ATTACK || current == ThreatState.PANIC)) {
            // Also initializes a fresh hold after a server reload, where runtime timers are absent.
            settlement.startAttackState(level.getGameTime(), MINIMUM_ATTACK_STATE_TICKS);
        }

        switch (current) {
            case NORMAL, ALERT -> {
                if (confirmedAttack) {
                    next = hasAnyGuards
                            ? ThreatState.UNDER_ATTACK
                            : ThreatState.PANIC;
                } else if (earlyWarning) {
                    next = ThreatState.ALERT;
                } else {
                    next = ThreatState.NORMAL;
                }
            }

            case UNDER_ATTACK -> {
                if (confirmedAttack && !hasAnyGuards) {
                    next = ThreatState.PANIC;
                } else if (!confirmedAttack && settlement.attackStateMinimumComplete(level.getGameTime())) {
                    next = ThreatState.RECOVERY;
                    settlement.startRecovery(
                            level.getGameTime(),
                            RECOVERY_DURATION_TICKS
                    );
                }
            }

            case PANIC -> {
                if (confirmedAttack && hasAnyGuards) {
                    next = ThreatState.UNDER_ATTACK;
                } else if (!confirmedAttack && settlement.attackStateMinimumComplete(level.getGameTime())) {
                    next = ThreatState.RECOVERY;
                    settlement.startRecovery(
                            level.getGameTime(),
                            RECOVERY_DURATION_TICKS
                    );
                }
            }

            case RECOVERY -> {
                if (confirmedAttack) {
                    next = hasAnyGuards
                            ? ThreatState.UNDER_ATTACK
                            : ThreatState.PANIC;
                } else if (settlement.recoveryComplete(level.getGameTime())) {
                    next = earlyWarning
                            ? ThreatState.ALERT
                            : ThreatState.NORMAL;
                }
            }

            case ABANDONED -> {
                // Threat logic leaves abandoned settlements unchanged.
            }
        }

        if (next != current) {
            boolean enteringAttack = (next == ThreatState.UNDER_ATTACK || next == ThreatState.PANIC)
                    && current != ThreatState.UNDER_ATTACK && current != ThreatState.PANIC;
            if (enteringAttack) {
                settlement.startAttackState(level.getGameTime(), MINIMUM_ATTACK_STATE_TICKS);
            } else if (next != ThreatState.UNDER_ATTACK && next != ThreatState.PANIC) {
                settlement.clearAttackState();
            }
            settlement.setThreatState(next);
            recordChronicle(level, settlement, current, next);
            SettlementManager.get(level).markDirty();

            announce(level, settlement, next);

            SettlersMod.LOGGER.info(
                    "Settlement '{}' threat state: {} -> {}. "
                            + "hostiles={}, inside={}, targetingResident={}, "
                            + "guardConfirmed={}, guards={}",
                    settlement.name(),
                    current,
                    next,
                    hostiles.size(),
                    hostilesInside,
                    hostileTargetingResident,
                    guardConfirmedAttack,
                    guards.size()
            );
        }
    }

    private static void recordChronicle(ServerLevel level, Settlement settlement,
                                        ThreatState previous, ThreatState next) {
        String event = switch (next) {
            case UNDER_ATTACK -> "attack_began";
            case PANIC -> "panic";
            case RECOVERY -> "recovery_began";
            case NORMAL -> previous == ThreatState.RECOVERY ? "peace_restored" : "";
            default -> "";
        };
        if (!event.isEmpty()) {
            settlement.recordChronicle(level.getGameTime(), event, "");
        }
    }

    private static boolean isRelevantHostile(Monster monster) {
        return monster.isAlive()
                && !monster.isRemoved()
                && !monster.isNoAi();
    }

    /// Audible and visible staging for threat-state changes.
    private static void announce(
            ServerLevel level,
            Settlement settlement,
            ThreatState next
    ) {
        var center = settlement.center();

        switch (next) {
            case ALERT -> level.playSound(
                    null,
                    center,
                    SoundEvents.BELL_BLOCK,
                    SoundSource.NEUTRAL,
                    2.0F,
                    1.2F
            );

            case UNDER_ATTACK -> {
                level.playSound(
                        null,
                        center,
                        SoundEvents.BELL_BLOCK,
                        SoundSource.NEUTRAL,
                        3.0F,
                        0.8F
                );

                level.sendParticles(
                        ParticleTypes.ANGRY_VILLAGER,
                        center.getX() + 0.5,
                        center.getY() + 2.0,
                        center.getZ() + 0.5,
                        10,
                        1.0,
                        0.5,
                        1.0,
                        0.01
                );
            }

            case PANIC -> level.playSound(
                    (Entity)null,
                    center,
                    SoundEvents.RAID_HORN.value(),
                    SoundSource.NEUTRAL,
                    2.5F,
                    1.1F
            );

            case NORMAL -> level.playSound(
                    null,
                    center,
                    SoundEvents.VILLAGER_WORK_MASON,
                    SoundSource.NEUTRAL,
                    1.0F,
                    1.0F
            );

            default -> {
            }
        }
    }
}
