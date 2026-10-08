package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.shared.CompetitionRanks;
import de.venomenon.cscxtool.shared.CscPoints;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/** The same full competition field and rank/points path for actual and hypothetical results. */
final class ShowPoints {
    record Score(int ballotPoints, int rank, int contestPoints) { }
    private ShowPoints() { }

    static Map<Long, Score> calculate(ResultData data, long showId, Long removedBallotId) {
        Map<Long, Integer> points = new LinkedHashMap<>();
        data.entries().stream().filter(e -> e.showId() == showId).forEach(e -> points.put(e.id(), 0));
        var ballots = data.ballots().stream().filter(b -> b.showId() == showId && "ABGESTIMMT".equals(b.status())
                && !Long.valueOf(b.id()).equals(removedBallotId)).map(ResultData.Ballot::id).collect(Collectors.toSet());
        if (ballots.isEmpty()) throw new IllegalArgumentException("Keine wertbare Stimmenbasis.");
        data.positions().stream().filter(p -> ballots.contains(p.ballotId()))
                .forEach(p -> points.computeIfPresent(p.entryId(), (id, sum) -> sum + CscPoints.pointsForRank(p.rank())));
        var ranks = CompetitionRanks.of(points);
        Map<Long, Score> result = new LinkedHashMap<>();
        points.forEach((id, sum) -> result.put(id, new Score(sum, ranks.get(id), CompetitionRanks.contestPoints(ranks.get(id)))));
        return result;
    }
}
