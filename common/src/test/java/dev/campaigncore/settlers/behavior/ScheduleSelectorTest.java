package dev.campaigncore.settlers.behavior;

import dev.campaigncore.settlers.MinecraftTestBase;
import dev.campaigncore.settlers.entity.data.SettlerRole;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleSelectorTest extends MinecraftTestBase {
    @Test
    void guardsPatrolAtNightWhileOtherResidentsSleep() {
        assertTrue(ScheduleSelector.patrolsAtNight(SettlerRole.GUARD));
        assertFalse(ScheduleSelector.patrolsAtNight(SettlerRole.WORKER));
        assertFalse(ScheduleSelector.patrolsAtNight(SettlerRole.CIVILIAN));
        assertFalse(ScheduleSelector.patrolsAtNight(SettlerRole.AUTHORITY));
    }

    @Test
    void personalBreaksStayBetweenNineAndThree() {
        for (int i = 0; i < 100; i++) {
            SettlerRoutine routine = SettlerRoutine.forSettler(new UUID(i, i * 31L + 7L));
            assertTrue(routine.breakStart() >= 3_000L);
            assertTrue(routine.breakStart() + SettlerRoutine.BREAK_DURATION <= 9_000L);
        }
    }

    @Test
    void personalBreakWindowHasStableBoundaries() {
        UUID id = UUID.fromString("0b3243a1-a02d-48bc-bb47-1fb4b76c19d4");
        SettlerRoutine routine = SettlerRoutine.forSettler(id);
        long start = routine.breakStart();
        assertFalse(routine.isBreak(start - 1));
        assertTrue(routine.isBreak(start));
        assertTrue(routine.isBreak(start + SettlerRoutine.BREAK_DURATION - 1));
        assertFalse(routine.isBreak(start + SettlerRoutine.BREAK_DURATION));
    }

    @Test
    void settlersReceiveVariedStartsAndDestinations() {
        Set<Long> starts = new HashSet<>();
        boolean sawHome = false;
        boolean sawWander = false;
        for (int i = 0; i < 32; i++) {
            UUID id = new UUID(0x1234L + i, 0x9876L * (i + 1));
            SettlerRoutine routine = SettlerRoutine.forSettler(id);
            starts.add(routine.breakStart());
            sawHome |= routine.breakAtHome();
            sawWander |= !routine.breakAtHome();
        }
        assertTrue(starts.size() > 24);
        assertTrue(sawHome && sawWander);
    }

    @Test
    void workTransitionsAndErrandCountsVaryByResident() {
        Set<Long> workStarts = new HashSet<>();
        Set<Long> workEnds = new HashSet<>();
        boolean sawOneErrand = false;
        boolean sawTwoErrands = false;
        for (int i = 0; i < 64; i++) {
            SettlerRoutine routine = SettlerRoutine.forSettler(new UUID(i * 17L + 3L, i * 101L + 9L));
            workStarts.add(routine.workStart());
            workEnds.add(routine.workEnd());
            sawTwoErrands |= routine.takesSecondErrand();
            sawOneErrand |= !routine.takesSecondErrand();
        }
        assertTrue(workStarts.size() > 48);
        assertTrue(workEnds.size() > 48);
        assertTrue(sawOneErrand && sawTwoErrands);
    }

    @Test
    void thrivingConversationWindowIsLonger() {
        SettlerRoutine routine = SettlerRoutine.forSettler(new UUID(44L, 91L));
        long lateInThrivingWindow = routine.conversationStart()
                + SettlerRoutine.CONVERSATION_OPPORTUNITY * 2L;
        assertFalse(routine.isConversationOpportunity(lateInThrivingWindow));
        assertTrue(routine.isThrivingConversationOpportunity(lateInThrivingWindow));
    }
}
