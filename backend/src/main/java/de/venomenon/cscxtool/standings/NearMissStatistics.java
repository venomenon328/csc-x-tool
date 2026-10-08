package de.venomenon.cscxtool.standings;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public record NearMissStatistics(List<Case> cases, List<Frequency> frequencies,
                                List<Long> frequencyWinnerIds, List<Long> smallestGapEntryIds) {
    public record BoundaryEntry(long entryId, long participationId, int ballotPoints, int showRank, int contestPoints) { }
    public record Case(long showId, long entryId, long participationId, int showRank, int ballotPoints,
                       int contestPoints, int gap, List<BoundaryEntry> lastPointGroup) { }
    public record Frequency(long participationId, int count, List<Long> entryIds) { }

    static NearMissStatistics calculate(ContestStandingsResponse standings) {
        List<Case> cases = new ArrayList<>();
        for (long showId : standings.includedShowIds()) {
            var cells = standings.rows().stream().flatMap(r -> r.shows().stream())
                    .filter(c -> c.showId() == showId && c.showRank() != null).toList();
            int first = cells.stream().mapToInt(ContestStandingsResponse.Cell::showRank).filter(r -> r > 15).min().orElse(0);
            int last = cells.stream().mapToInt(ContestStandingsResponse.Cell::showRank).filter(r -> r <= 15).max().orElse(0);
            if (first == 0 || last == 0) continue;
            var boundary = standings.rows().stream().flatMap(row -> row.shows().stream()
                    .filter(c -> c.showId() == showId && Integer.valueOf(last).equals(c.showRank()))
                    .map(c -> new BoundaryEntry(c.entryId(), row.participationId(), c.ballotPoints(), c.showRank(), c.contestPoints())))
                    .sorted(Comparator.comparingLong(BoundaryEntry::entryId)).toList();
            for (var row : standings.rows()) for (var cell : row.shows()) {
                if (cell.showId() == showId && Integer.valueOf(first).equals(cell.showRank())) {
                    int gap = boundary.getFirst().ballotPoints() - cell.ballotPoints();
                    if (gap > 0) cases.add(new Case(showId, cell.entryId(), row.participationId(), first,
                            cell.ballotPoints(), cell.contestPoints(), gap, boundary));
                }
            }
        }
        cases.sort(Comparator.comparingLong(Case::showId).thenComparingLong(Case::entryId));
        var frequencies = standings.rows().stream().map(row -> {
            var ids = cases.stream().filter(c -> c.participationId() == row.participationId()).map(Case::entryId).toList();
            return new Frequency(row.participationId(), ids.size(), ids);
        }).filter(f -> f.count() > 0).sorted(Comparator.comparingInt(Frequency::count).reversed()
                .thenComparingLong(Frequency::participationId)).toList();
        int maximum = frequencies.stream().mapToInt(Frequency::count).max().orElse(0);
        int minimum = cases.stream().mapToInt(Case::gap).min().orElse(0);
        return new NearMissStatistics(List.copyOf(cases), frequencies,
                frequencies.stream().filter(f -> f.count() == maximum).map(Frequency::participationId).toList(),
                cases.stream().filter(c -> c.gap() == minimum).map(Case::entryId).toList());
    }
}
