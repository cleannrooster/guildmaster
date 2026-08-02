package dev.campaigncore.settlers.behavior;

import java.util.UUID;

/// Stable UUID-derived personal routine. No save data is needed, and the same resident keeps the
/// same habits across reloads.
public record SettlerRoutine(
        long workStart,
        long workEnd,
        long breakStart,
        boolean breakAtHome,
        long firstErrandStart,
        long secondErrandStart,
        boolean takesSecondErrand,
        long conversationStart,
        boolean conversational
) {
    public static final long BREAK_DURATION = 1_000L;
    public static final long ERRAND_DURATION = 400L;
    public static final long CONVERSATION_OPPORTUNITY = 120L;

    public static SettlerRoutine forSettler(UUID id) {
        long seed = id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17);
        long workStart = 400L + bounded(mix(seed), 1_201L);       // 6:24–7:36 AM
        long workEnd = 10_400L + bounded(mix(seed + 1), 1_201L); // 4:24–5:36 PM
        long breakStart = 3_000L + bounded(mix(seed + 2), 5_001L);
        long firstErrand = 1_800L + bounded(mix(seed + 3), 1_000L);
        long secondErrand = 9_100L + bounded(mix(seed + 4), 900L);
        return new SettlerRoutine(
                workStart,
                workEnd,
                breakStart,
                (mix(seed + 5) & 1L) == 0L,
                firstErrand,
                secondErrand,
                bounded(mix(seed + 6), 100L) < 55L,
                breakStart + 240L + bounded(mix(seed + 7), 400L),
                bounded(mix(seed + 8), 100L) < 45L);
    }

    public boolean isBreak(long dayTime) {
        return dayTime >= breakStart && dayTime < breakStart + BREAK_DURATION;
    }

    public boolean isErrand(long dayTime) {
        return inWindow(dayTime, firstErrandStart, ERRAND_DURATION)
                || (takesSecondErrand && inWindow(dayTime, secondErrandStart, ERRAND_DURATION));
    }

    public boolean isConversationOpportunity(long dayTime) {
        return conversational && inWindow(dayTime, conversationStart, CONVERSATION_OPPORTUNITY);
    }

    public boolean isThrivingConversationOpportunity(long dayTime) {
        return inWindow(dayTime, conversationStart, CONVERSATION_OPPORTUNITY * 3L);
    }

    private static boolean inWindow(long value, long start, long duration) {
        return value >= start && value < start + duration;
    }

    private static long bounded(long value, long bound) {
        return Math.floorMod(value, bound);
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
