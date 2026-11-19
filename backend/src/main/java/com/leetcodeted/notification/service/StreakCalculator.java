package com.leetcodeted.notification.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class StreakCalculator {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long DAY_SECONDS = 86400;

    private StreakCalculator() {}

    public record StreakResult(int currentStreak, int longestStreak) {}

    /**
     * Compute streaks from a submissionCalendar map (epoch-seconds → count).
     * Mirrors the TypeScript computeStreaks logic, with optional excused dates.
     */
    public static StreakResult compute(Map<Long, Integer> calendar, Set<String> excusedDates, long nowMs) {
        Set<String> solved = new HashSet<>();
        for (var entry : calendar.entrySet()) {
            if (entry.getValue() > 0) {
                solved.add(toDateKey(entry.getKey()));
            }
        }
        Set<String> excused = excusedDates != null ? excusedDates : Set.of();

        long todayEpoch = (nowMs / 1000 / DAY_SECONDS) * DAY_SECONDS;
        String todayKey = toDateKey(todayEpoch);
        long t = todayEpoch;
        if (!solved.contains(todayKey) && !excused.contains(todayKey)) {
            t -= DAY_SECONDS;
        }

        int currentStreak = 0;
        while (true) {
            String key = toDateKey(t);
            if (solved.contains(key)) {
                currentStreak++;
                t -= DAY_SECONDS;
            } else if (excused.contains(key)) {
                t -= DAY_SECONDS;
            } else {
                break;
            }
        }

        // Longest streak: scan all dates from earliest to now
        int longestStreak = 0;
        if (!calendar.isEmpty()) {
            long earliest = calendar.keySet().stream().mapToLong(Long::longValue).min().orElse(todayEpoch);
            long start = (earliest / DAY_SECONDS) * DAY_SECONDS;
            int run = 0;
            for (long s = start; s <= todayEpoch; s += DAY_SECONDS) {
                String key = toDateKey(s);
                if (solved.contains(key)) {
                    run++;
                    longestStreak = Math.max(longestStreak, run);
                } else if (!excused.contains(key)) {
                    run = 0;
                }
            }
        }

        return new StreakResult(currentStreak, longestStreak);
    }

    private static String toDateKey(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(VN_ZONE).toLocalDate().toString();
    }
}
