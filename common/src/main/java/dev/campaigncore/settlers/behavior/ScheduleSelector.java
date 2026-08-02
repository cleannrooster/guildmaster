package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.entity.SettlerEntity;
import dev.campaigncore.settlers.entity.data.SettlerRole;
import dev.campaigncore.settlers.settlement.Settlement;
import dev.campaigncore.settlers.settlement.SettlementRecruitmentService;
import dev.campaigncore.settlers.settlement.MoraleState;
import dev.campaigncore.settlers.settlement.SettlementMoraleModel;
import dev.campaigncore.settlers.settlement.ThreatState;
import net.minecraft.server.level.ServerLevel;

/// Beta schedule logic:
/// role + time of day + threat state -> one active behavior.
///
/// The SettlerBehavior interface remains stable if this selector is later
/// replaced with role-keyed JSON schedules.
public final class ScheduleSelector {
    private ScheduleSelector() {
    }

    public static SettlerBehavior select(
            SettlerEntity settler,
            Settlement settlement,
            ServerLevel level
    ) {
        ThreatState threat = settlement.threatState();
        SettlerRole role = settler.role();

        boolean defender =
                role == SettlerRole.GUARD
                        || role == SettlerRole.AUTHORITY;

        /*
         * An attack is confirmed. Defenders muster while everyone else shelters.
         */
        if (threat == ThreatState.UNDER_ATTACK) {
            return defender
                    ? new MusterBehavior()
                    : new FleeToShelterBehavior();
        }

        /*
         * A confirmed attack exists without adequate guards.
         */
        if (threat == ThreatState.PANIC) {
            return defender
                    ? new MusterBehavior()
                    : new PanicBehavior();
        }

        /*
         * Possible danger near the perimeter.
         *
         * Guards and authorities investigate. Other residents continue their
         * ordinary schedules until an attack is confirmed.
         */
        if (threat == ThreatState.ALERT && defender) {
            return new MusterBehavior();
        }

        /*
         * The attack has ended, but civilians remain sheltered briefly.
         * Defenders continue holding their positions during the cooldown.
         */
        if (threat == ThreatState.RECOVERY) {
            return defender
                    ? new MusterBehavior()
                    : new FleeToShelterBehavior();
        }

        if (level.isNight()) {
            return peacefulNightBehavior(role);
        }

        long dayTime = Math.floorMod(level.getDayTime(), 24_000L);
        MoraleState morale = SettlementMoraleModel.evaluate(level, settlement);
        if (morale.allowsOptionalConversation() && settler.hasActiveConversation(level.getGameTime())) {
            return new ConversationBehavior();
        }

        SettlerRoutine routine = SettlerRoutine.forSettler(settler.getUUID());
        if (morale.allowsOptionalConversation() && (routine.isConversationOpportunity(dayTime)
                || (morale == MoraleState.THRIVING && routine.isThrivingConversationOpportunity(dayTime)))) {
            return new ConversationBehavior();
        }
        if (routine.isBreak(dayTime)) {
            return morale.spendsBreakAtHome(routine.breakAtHome())
                    ? new BreakAtHomeBehavior()
                    : new BreakWanderBehavior();
        }

        if (dayTime < routine.workStart()) {
            return new BreakAtHomeBehavior();
        }
        if (dayTime >= routine.workEnd()) {
            return morale == MoraleState.DISTRESSED ? new BreakAtHomeBehavior() : new SocializeBehavior();
        }
        if (morale.takesOptionalErrands() && routine.isErrand(dayTime)) {
            return new ErrandBehavior();
        }
        if (SettlementRecruitmentService.hasJobOffer(settlement, settler.getUUID())) {
            return new SeekEmploymentBehavior();
        }

        return switch (role) {
            case GUARD -> new PatrolBehavior();

            case WORKER, AUTHORITY -> new WorkBehavior();

            case CIVILIAN -> isEvening(level)
                    ? new SocializeBehavior()
                    : new WorkBehavior();

            case TRAVELER -> new SocializeBehavior();
        };
    }

    static SettlerBehavior peacefulNightBehavior(SettlerRole role) {
        return patrolsAtNight(role) ? new PatrolBehavior() : new SleepBehavior();
    }

    static boolean patrolsAtNight(SettlerRole role) {
        return role == SettlerRole.GUARD;
    }

    /// Roughly the final period before night.
    private static boolean isEvening(ServerLevel level) {
        long dayTime = Math.floorMod(level.getDayTime(), 24_000L);
        return dayTime >= 11_000L && dayTime < 13_000L;
    }

}
