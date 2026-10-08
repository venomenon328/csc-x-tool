package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.CscPoints;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import static de.venomenon.cscxtool.standings.ContestStatisticsResponse.*;

final class ContestStatistics {
    private ContestStatistics() { }

    static ContestStatisticsResponse calculate(ResultData data, CountryCatalog countries) {
        var standings = ContestStandings.calculate(data, countries);
        var shows = data.shows().stream().sorted(Comparator.comparingInt(ResultData.Show::number)).toList();
        Map<Long, Map<Long, ResultData.Entry>> entries = new HashMap<>();
        for (var entry : data.entries()) {
            if (entry.participationId() != null) entries.computeIfAbsent(entry.showId(), ignored -> new HashMap<>())
                    .put(entry.participationId(), entry);
        }
        Map<Long, Map<Long, ResultData.Ballot>> ballots = new HashMap<>();
        data.ballots().forEach(b -> ballots.computeIfAbsent(b.showId(), ignored -> new HashMap<>()).put(b.participationId(), b));
        Map<Long, Map<Long, Integer>> positions = new HashMap<>();
        data.positions().forEach(p -> positions.computeIfAbsent(p.ballotId(), ignored -> new HashMap<>()).put(p.entryId(), p.rank()));
        Map<Direction, Relation> indexed = new LinkedHashMap<>();
        for (var giver : data.participants()) for (var receiver : data.participants()) {
            if (giver.participantId() == receiver.participantId()) continue;
            List<Evaluation> evidence = new ArrayList<>();
            for (var show : shows) {
                var entry = entries.getOrDefault(show.id(), Map.of()).get(receiver.id());
                var ballot = ballots.getOrDefault(show.id(), Map.of()).get(giver.id());
                String state;
                Integer rank = null, points = null;
                if (show.closedAt() == null) state = "NOT_COUNTED";
                else if (entry == null) state = "NO_ENTRY";
                else if (ballot == null || "UNERFASST".equals(ballot.status())) state = "UNRECORDED";
                else if ("NICHT_ABGESTIMMT".equals(ballot.status())) state = "NOT_VOTED";
                else {
                    // Closed results have already passed the shared canonical closure validation.
                    rank = positions.getOrDefault(ballot.id(), Map.of()).get(entry.id());
                    points = rank == null ? 0 : CscPoints.pointsForRank(rank);
                    state = rank == null ? "OUTSIDE_TOP_15" : "POINTS";
                }
                evidence.add(new Evaluation(show.id(), entry == null ? null : entry.id(), state, rank, points));
            }
            int total = evidence.stream().filter(e -> e.points() != null).mapToInt(Evaluation::points).sum();
            int count = (int) evidence.stream().filter(e -> e.points() != null).count();
            int positive = (int) evidence.stream().filter(e -> e.points() != null && e.points() > 0).count();
            int highest = (int) evidence.stream().filter(e -> Integer.valueOf(1).equals(e.ballotRank())).count();
            indexed.put(new Direction(giver.id(), receiver.id()), new Relation(giver.id(), receiver.id(), total,
                    positive, count, count == 0 ? null : (double) total / count, highest, List.copyOf(evidence)));
        }
        var relations = indexed.values().stream().sorted(Comparator.comparingInt(Relation::points).reversed()
                .thenComparingLong(Relation::giverId).thenComparingLong(Relation::receiverId)).toList();
        List<Pair> pairs = new ArrayList<>();
        for (var forward : indexed.values()) {
            if (forward.giverId() >= forward.receiverId()) continue;
            var reverse = indexed.get(new Direction(forward.receiverId(), forward.giverId()));
            var reverseShows = reverse.shows().stream().collect(Collectors.toMap(Evaluation::showId, e -> e));
            var common = forward.shows().stream().filter(e -> e.points() != null && reverseShows.get(e.showId()).points() != null).toList();
            if (common.isEmpty()) continue;
            int x = common.stream().mapToInt(Evaluation::points).sum();
            int y = common.stream().mapToInt(e -> reverseShows.get(e.showId()).points()).sum();
            pairs.add(new Pair(forward.giverId(), forward.receiverId(), x, y, Math.min(x, y), Math.abs(x - y),
                    x == y ? null : x > y ? forward.giverId() : forward.receiverId(), common.stream().map(Evaluation::showId).toList()));
        }
        List<EntryAward> awards = new ArrayList<>();
        for (var entry : data.entries()) {
            if (!standings.includedShowIds().contains(entry.showId())) continue;
            var evaluations = relations.stream().filter(r -> Long.valueOf(r.receiverId()).equals(entry.participationId()))
                    .flatMap(r -> r.shows().stream()).filter(e -> Long.valueOf(entry.id()).equals(e.entryId()) && e.points() != null).toList();
            int count = (int) evaluations.stream().filter(e -> Integer.valueOf(1).equals(e.ballotRank())).count();
            awards.add(new EntryAward(entry.id(), count, evaluations.size(), rate(count, evaluations.size())));
        }
        var awardsByEntry = awards.stream().collect(Collectors.toMap(EntryAward::entryId, a -> a));
        List<Profile> profiles = new ArrayList<>();
        for (var row : standings.rows()) {
            var incoming = relations.stream().filter(r -> r.receiverId() == row.participationId()).toList();
            var outgoing = relations.stream().filter(r -> r.giverId() == row.participationId()).toList();
            var ownAwards = row.shows().stream().filter(c -> c.entryId() != null).map(c -> awardsByEntry.get(c.entryId())).toList();
            int count = ownAwards.stream().mapToInt(EntryAward::twentyFives).sum();
            int opportunities = ownAwards.stream().mapToInt(EntryAward::opportunities).sum();
            int top15 = (int) row.shows().stream().filter(c -> c.showRank() != null && c.showRank() <= 15).count();
            int podium = (int) row.shows().stream().filter(c -> c.showRank() != null && c.showRank() <= 3).count();
            int wins = (int) row.shows().stream().filter(c -> Integer.valueOf(1).equals(c.showRank())).count();
            profiles.add(new Profile(row.participationId(), top(incoming).stream().map(Relation::giverId).toList(),
                    top(outgoing).stream().map(Relation::receiverId).toList(), count, opportunities, rate(count, opportunities),
                    ownAwards.size(), top15, podium, wins, runs(row, shows, false), runs(row, shows, true)));
        }
        var records = new Records(winners(pairs, Pair::partnership).stream().map(p -> new Direction(p.firstId(), p.secondId())).toList(),
                winners(pairs, Pair::difference).stream().map(p -> new Direction(p.firstId(), p.secondId())).toList(),
                winners(profiles, Profile::twentyFives).stream().map(Profile::participationId).toList(),
                winners(awards, EntryAward::twentyFives).stream().map(EntryAward::entryId).toList(),
                winners(profiles, p -> longest(p.pointRuns())).stream().map(Profile::participationId).toList(),
                winners(profiles, p -> longest(p.podiumRuns())).stream().map(Profile::participationId).toList(),
                winners(profiles, Profile::top15Count).stream().map(Profile::participationId).toList(),
                winners(profiles, Profile::podiumCount).stream().map(Profile::participationId).toList());
        return new ContestStatisticsResponse(standings, data.entries(), relations,
                top(relations).stream().map(r -> new Direction(r.giverId(), r.receiverId())).toList(),
                List.copyOf(pairs), List.copyOf(profiles), List.copyOf(awards), records, PreferenceStatistics.calculate(data),
                NearMissStatistics.calculate(standings));
    }

    private static Double rate(int count, int basis) { return basis == 0 ? null : (double) count / basis; }
    private static List<Relation> top(List<Relation> ordered) {
        var positive = ordered.stream().filter(r -> r.points() > 0).toList();
        if (positive.size() <= 5) return positive;
        int boundary = positive.get(4).points();
        return positive.stream().filter(r -> r.points() >= boundary).toList();
    }
    private static <T> List<T> winners(List<T> values, ToIntFunction<T> metric) {
        int maximum = values.stream().mapToInt(metric).max().orElse(0);
        return maximum <= 0 ? List.of() : values.stream().filter(v -> metric.applyAsInt(v) == maximum).toList();
    }
    private static int longest(List<Run> runs) { return runs.isEmpty() ? 0 : runs.getFirst().length(); }

    private static List<Run> runs(ContestStandingsResponse.Row row, List<ResultData.Show> shows, boolean podium) {
        var cells = row.shows().stream().collect(Collectors.toMap(ContestStandingsResponse.Cell::showId, c -> c));
        List<Run> all = new ArrayList<>();
        List<Long> current = new ArrayList<>();
        int previous = -1;
        for (var show : shows) {
            var cell = cells.get(show.id());
            boolean qualifies = cell.showRank() != null && (podium ? cell.showRank() <= 3 : cell.contestPoints() > 0);
            if (!qualifies || show.number() != previous + 1) { finish(all, current); current = new ArrayList<>(); }
            if (qualifies) current.add(show.id());
            previous = show.number();
        }
        finish(all, current);
        return winners(all, Run::length);
    }
    private static void finish(List<Run> runs, List<Long> current) {
        if (!current.isEmpty()) runs.add(new Run(current.size(), current.getFirst(), current.getLast(), List.copyOf(current)));
    }
}
