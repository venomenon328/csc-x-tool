package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.shared.CompetitionRanks;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.venomenon.cscxtool.standings.ContestStandingsResponse.*;

final class ContestStandings {
    private ContestStandings() { }

    static ContestStandingsResponse calculate(ResultData data, CountryCatalog countries) {
        Map<Long, Integer> totals = new LinkedHashMap<>();
        Map<Long, List<Cell>> cells = new HashMap<>();
        Map<Long, List<Step>> history = new HashMap<>();
        data.participants().forEach(p -> {
            totals.put(p.id(), 0); cells.put(p.id(), new ArrayList<>()); history.put(p.id(), new ArrayList<>());
        });
        List<ShowStatus> shows = new ArrayList<>();
        List<Long> included = new ArrayList<>();
        Map<Long, Integer> previousRanks = null;
        Map<Long, Integer> lastChanges = new HashMap<>();
        for (var show : data.shows()) {
            boolean closed = show.closedAt() != null;
            shows.add(new ShowStatus(show.id(), show.number(), show.name(), ContestResultService.status(data, show), show.closedAt()));
            var entries = data.entries().stream().filter(e -> e.showId() == show.id()).toList();
            Map<Long, ShowPoints.Score> scores = closed ? ShowPoints.calculate(data, show.id(), null) : Map.of();
            if (closed) {
                included.add(show.id());
            }
            for (var participant : data.participants()) {
                var entry = entries.stream().filter(e -> Long.valueOf(participant.id()).equals(e.participationId())).findFirst().orElse(null);
                Cell cell;
                if (!closed) cell = new Cell(show.id(), "NOT_COUNTED", null, null, null, null, null, null);
                else if (entry == null) cell = new Cell(show.id(), "NO_ENTRY", null, null, null, null, null, null);
                else {
                    var score = scores.get(entry.id());
                    int rank = score.rank();
                    int awarded = score.contestPoints();
                    totals.compute(participant.id(), (id, sum) -> sum + awarded);
                    cell = new Cell(show.id(), "SCORED_ENTRY", entry.id(), entry.artist(), entry.title(), score.ballotPoints(), rank, awarded);
                }
                cells.get(participant.id()).add(cell);
            }
            Map<Long, Integer> ranks = closed ? CompetitionRanks.of(totals) : Map.of();
            for (var participant : data.participants()) {
                long id = participant.id();
                Integer change = closed && previousRanks != null ? previousRanks.get(id) - ranks.get(id) : null;
                history.get(id).add(new Step(show.id(), show.number(), closed, closed ? totals.get(id) : null, ranks.get(id), change));
                if (closed) lastChanges.put(id, change);
            }
            if (closed) previousRanks = ranks;
        }
        Map<Long, Integer> finalRanks = included.isEmpty() ? Map.of() : CompetitionRanks.of(totals);
        List<Row> rows = data.participants().stream().map(p -> new Row(p.id(), p.participantId(), p.name(), p.countryCode(),
                countries.findRequired(p.countryCode()).name(), finalRanks.get(p.id()), totals.get(p.id()), lastChanges.get(p.id()),
                List.copyOf(cells.get(p.id())), List.copyOf(history.get(p.id()))))
                .sorted(Comparator.comparingInt(Row::totalPoints).reversed()).toList();
        return new ContestStandingsResponse(data.contestId(), List.copyOf(shows), List.copyOf(included), rows);
    }
}
