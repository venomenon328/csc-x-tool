package de.venomenon.cscxtool.standings;

import java.util.List;
import de.venomenon.cscxtool.standings.ContestStatisticsResponse.Direction;

/** Shared ballots occur once per show, never once per pair. All IDs are stable. */
public record PreferenceStatisticsResponse(List<ShowBasis> showBases, List<SimilarityPair> pairs,
        List<Direction> parallelOrder, List<EntryPreference> entries, List<Long> audienceOrder,
        List<Long> polarizationOrder, List<ParticipantPreference> participants, List<Long> exclusiveOrder, PreferenceRecords records) {
    public record Metric(String numerator, String denominator, Double value, String reason) { }
    public record BallotPoint(long entryId, int rank, int points) { }
    public record BallotBasis(long ballotId, long participationId, List<BallotPoint> positions) { }
    public record ShowBasis(long showId, List<BallotBasis> ballots) { }
    public record PairShow(long showId, Metric similarity, int comparisonEntries, List<Long> excludedEntryIds) { }
    public record SimilarityPair(long firstId, long secondId, Metric similarity, int comparedShows, List<PairShow> shows) { }
    public record HistogramBin(int points, int count) { }
    public record EntryPreference(long entryId, int evaluations, int positiveEvaluations, int sumPoints,
            int twentyFives, Metric audienceRate, Metric variance, Double standardDeviation,
            boolean polarizationEligible, List<HistogramBin> histogram, Long exclusiveGiverId, int exclusivePoints) { }
    public record ConsensusShow(long showId, Metric similarity, int otherBallots, int comparisonEntries,
            Long excludedEntryId, int ownPointSum, int fieldPointSum) { }
    public record ParticipantPreference(long participationId, Metric consensus, int comparedShows,
            List<ConsensusShow> shows, int exclusivePoints, List<Long> exclusiveEntryIds, List<Long> exclusiveTwentyFiveEntryIds) { }
    public record PreferenceRecords(List<Direction> twins, List<Direction> parallels, List<Long> audienceEntryIds,
            List<Long> polarizationEntryIds, List<Long> exclusiveParticipantIds, List<Long> consensusParticipantIds) { }
}
