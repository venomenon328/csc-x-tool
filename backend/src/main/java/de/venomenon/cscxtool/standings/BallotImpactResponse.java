package de.venomenon.cscxtool.standings;

import java.util.List;

/** Self-contained actual and hypothetical values from exactly one canonical read snapshot. */
public record BallotImpactResponse(ContestStandingsResponse standings, long showId,
        PreferenceStatisticsResponse.BallotBasis removedBallot, int validBallots, int remainingBallots,
        String state, List<Long> actualWinnerEntryIds, List<Long> hypotheticalWinnerEntryIds, List<Comparison> comparisons) {
    public record Outcome(int ballotPoints, int showRank, int contestPoints, int contestTotal) { }
    public record Delta(int ballotPoints, int showRank, int contestPoints, int contestTotal) { }
    public record Comparison(long entryId, long participationId, String artist, String title,
                             Outcome actual, Outcome hypothetical, Delta delta, String boundaryChange) { }
}
