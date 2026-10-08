package de.venomenon.cscxtool.standings;

import java.util.List;

public record ContestStandingsResponse(long contestId, List<ShowStatus> shows, List<Long> includedShowIds, List<Row> rows) {
    public record ShowStatus(long showId, int showNumber, String name, String status, String closedAt) { }
    public record Row(long participationId, long participantId, String displayName, String countryCode,
                      String countryName, Integer rank, int totalPoints, Integer rankChange,
                      List<Cell> shows, List<Step> history) { }
    public record Cell(long showId, String state, Long entryId, String artist, String title,
                       Integer ballotPoints, Integer showRank, Integer contestPoints) { }
    public record Step(long showId, int showNumber, boolean included, Integer totalPoints, Integer rank, Integer rankChange) { }
}
