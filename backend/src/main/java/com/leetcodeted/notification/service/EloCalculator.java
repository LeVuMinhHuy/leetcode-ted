package com.leetcodeted.notification.service;

public final class EloCalculator {

    private static final int BASE = 800;
    private static final int EASY_WEIGHT = 2;
    private static final int MEDIUM_WEIGHT = 6;
    private static final int HARD_WEIGHT = 16;
    private static final int STREAK_WEIGHT = 10;

    private EloCalculator() {}

    public static int compute(int easy, int medium, int hard, int currentStreak) {
        return BASE + easy * EASY_WEIGHT + medium * MEDIUM_WEIGHT + hard * HARD_WEIGHT + currentStreak * STREAK_WEIGHT;
    }
}
