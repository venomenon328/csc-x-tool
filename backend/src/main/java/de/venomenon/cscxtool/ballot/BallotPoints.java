package de.venomenon.cscxtool.ballot;

import de.venomenon.cscxtool.shared.CscPoints;

/** Central, reproducible mapping for the CSC ranks; snapshot items intentionally do not persist points. */
final class BallotPoints {

    private BallotPoints() {
    }

    static int pointsForRank(int rank) {
        return CscPoints.pointsForRank(rank);
    }
}
