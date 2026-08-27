package de.venomenon.cscxtool.ballot;

/** Central, reproducible mapping for the CSC ranks; snapshot items intentionally do not persist points. */
final class BallotPoints {

    private static final int[] POINTS = {25, 20, 16, 13, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};

    private BallotPoints() {
    }

    static int pointsForRank(int rank) {
        if (rank < 1 || rank > POINTS.length) {
            throw new IllegalArgumentException("Points are defined only for ranks 1 through 15.");
        }
        return POINTS[rank - 1];
    }
}
