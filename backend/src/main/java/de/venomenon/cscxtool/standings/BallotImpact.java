package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.shared.CscPoints;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import static de.venomenon.cscxtool.standings.BallotImpactResponse.*;

final class BallotImpact {
    private BallotImpact() { }

    static BallotImpactResponse calculate(ResultData data, CountryCatalog countries, long showId, long ballotId) {
        var show = data.shows().stream().filter(s -> s.id() == showId).findFirst().orElse(null);
        if (show == null || show.closedAt() == null) throw conflict("Nur abgeschlossene Shows dieser Ausgabe sind simulierbar.");
        if (!ResultClosureRules.reasons(data, show).isEmpty()) throw conflict("Die aktuelle Stimmenbasis ist nicht gültig.");
        var ballot = data.ballots().stream().filter(b -> b.showId() == showId && b.id() == ballotId
                && "ABGESTIMMT".equals(b.status())).findFirst().orElseThrow(() -> conflict("Bitte einen gültigen veröffentlichten Stimmzettel dieser Show wählen."));
        int count = (int) data.ballots().stream().filter(b -> b.showId() == showId && "ABGESTIMMT".equals(b.status())).count();
        var original = ContestStandings.calculate(data, countries);
        // Do not pass the final-ballot case to the rank kernel: there is no alternative competition.
        Map<Long, ShowPoints.Score> alternative = count > 1 ? ShowPoints.calculate(data, showId, ballotId) : Map.of();
        var comparisons = original.rows().stream().flatMap(row -> row.shows().stream()
                .filter(c -> c.showId() == showId && c.entryId() != null).map(cell -> {
                    var actual = new Outcome(cell.ballotPoints(), cell.showRank(), cell.contestPoints(), row.totalPoints());
                    var score = alternative.get(cell.entryId());
                    Outcome hypothetical = score == null ? null : new Outcome(score.ballotPoints(), score.rank(), score.contestPoints(),
                            row.totalPoints() - cell.contestPoints() + score.contestPoints());
                    Delta delta = hypothetical == null ? null : new Delta(hypothetical.ballotPoints() - actual.ballotPoints(),
                            actual.showRank() - hypothetical.showRank(), hypothetical.contestPoints() - actual.contestPoints(),
                            hypothetical.contestTotal() - actual.contestTotal());
                    String boundary = hypothetical == null ? "NOT_EVALUABLE" : actual.contestPoints() == 0 && hypothetical.contestPoints() > 0 ? "ENTERED"
                            : actual.contestPoints() > 0 && hypothetical.contestPoints() == 0 ? "LEFT" : "UNCHANGED";
                    return new Comparison(cell.entryId(), row.participationId(), cell.artist(), cell.title(), actual, hypothetical, delta, boundary);
                })).sorted(Comparator.comparingInt((Comparison c) -> c.actual().showRank()).thenComparingLong(Comparison::entryId)).toList();
        var basis = new PreferenceStatisticsResponse.BallotBasis(ballot.id(), ballot.participationId(), data.positions().stream()
                .filter(p -> p.ballotId() == ballotId).sorted(Comparator.comparingInt(ResultData.Position::rank))
                .map(p -> new PreferenceStatisticsResponse.BallotPoint(p.entryId(), p.rank(), CscPoints.pointsForRank(p.rank()))).toList());
        return new BallotImpactResponse(original, showId, basis, count, count - 1, count > 1 ? "EVALUABLE" : "NO_REMAINING_BALLOT",
                comparisons.stream().filter(c -> c.actual().showRank() == 1).map(Comparison::entryId).toList(),
                comparisons.stream().filter(c -> c.hypothetical() != null && c.hypothetical().showRank() == 1).map(Comparison::entryId).toList(), comparisons);
    }

    private static ApiConflictException conflict(String message) { return new ApiConflictException("BALLOT_IMPACT_UNAVAILABLE", message); }
}
