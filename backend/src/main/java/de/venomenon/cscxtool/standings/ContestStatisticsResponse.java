package de.venomenon.cscxtool.standings;

import java.util.List;

/** One snapshot, including all drilldown evidence. IDs always refer to contest participations. */
public record ContestStatisticsResponse(ContestStandingsResponse standings, List<ResultData.Entry> entries,
        List<Relation> relations, List<Direction> topRelations, List<Pair> pairs,
        List<Profile> profiles, List<EntryAward> entryAwards, Records records, PreferenceStatisticsResponse preferences) {
    public record Direction(long giverId, long receiverId) { }
    public record Evaluation(long showId, Long entryId, String state, Integer ballotRank, Integer points) { }
    public record Relation(long giverId, long receiverId, int points, int scoredShows, int opportunities,
                           Double average, int twentyFives, List<Evaluation> shows) { }
    public record Pair(long firstId, long secondId, int firstToSecond, int secondToFirst,
                       int partnership, int difference, Long strongerGiverId, List<Long> commonShowIds) { }
    public record Run(int length, long firstShowId, long lastShowId, List<Long> showIds) { }
    public record Profile(long participationId, List<Long> topGiverIds, List<Long> topReceiverIds,
                          int twentyFives, int opportunities, Double twentyFiveRate, int countedEntries,
                          int top15Count, int podiumCount, int wins, List<Run> pointRuns, List<Run> podiumRuns) { }
    public record EntryAward(long entryId, int twentyFives, int opportunities, Double rate) { }
    public record Records(List<Direction> partnerships, List<Direction> unrequited,
                          List<Long> twentyFiveParticipantIds, List<Long> twentyFiveEntryIds,
                          List<Long> pointRunParticipantIds, List<Long> podiumRunParticipantIds,
                          List<Long> top15ParticipantIds, List<Long> podiumParticipantIds) { }
}
