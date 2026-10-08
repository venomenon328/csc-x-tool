package de.venomenon.cscxtool.standings;

import java.util.List;

/** Canonical input, independent of the anonymous personal-voting projection. */
public record ResultData(long contestId, boolean current, List<Show> shows, List<Participation> participants,
                         List<Entry> entries, List<Ballot> ballots, List<Position> positions) {
    public record Show(long id, int number, String name, boolean entryListComplete, String closedAt) { }
    public record Participation(long id, long participantId, String name, String countryCode) { }
    public record Entry(long id, long showId, Long participationId, String artist, String title) { }
    public record Ballot(long id, long showId, long participationId, String status) { }
    public record Position(long ballotId, long entryId, int rank) { }
}
