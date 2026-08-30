package de.venomenon.cscxtool.shared;

import java.util.Set;

/** One source of truth for rank-derived points; the legacy set validates only archived P7 values. */
public final class CscPoints {

    private static final int[] POINTS_BY_RANK = {25, 20, 16, 13, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    private static final Set<Integer> LEGACY_RECEIVED_SCORE_POINTS = Set.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 16, 20, 25);

    private CscPoints() {
    }

    public static int pointsForRank(int rank) {
        if (rank < 1 || rank > POINTS_BY_RANK.length) {
            throw new IllegalArgumentException("Points are defined only for ranks 1 through 15.");
        }
        return POINTS_BY_RANK[rank - 1];
    }

    public static boolean isAllowedLegacyReceivedScore(int points) {
        return LEGACY_RECEIVED_SCORE_POINTS.contains(points);
    }
}
