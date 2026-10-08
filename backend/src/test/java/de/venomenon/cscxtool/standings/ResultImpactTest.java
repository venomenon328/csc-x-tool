package de.venomenon.cscxtool.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** All oracles use complete, self-vote-free 15-position / 140-point published ballots. */
class ResultImpactTest {
    private final CountryCatalog countries = new CountryCatalog(new ObjectMapper());
    private static final String CLOSED = "2026-10-08T00:00:00Z";

    @Test void s3bT01UsesActualGroupsAndFullAwardsAtTiedBoundaries() {
        var a = order(1); var b = order(1); b.set(14, 16);
        var data = fixture(17, List.of(a, b), List.of(18, 19));
        var statistics = ContestStatistics.calculate(data, countries);
        assertThat(statistics.standings().rows().stream().mapToInt(ContestStandingsResponse.Row::totalPoints).sum()).isEqualTo(141);
        var miss = statistics.nearMisses().cases().getFirst();
        assertThat(miss.entryId()).isEqualTo(117);
        assertThat(miss.showRank()).isEqualTo(17);
        assertThat(miss.gap()).isEqualTo(1);
        assertThat(miss.lastPointGroup()).extracting(NearMissStatistics.BoundaryEntry::entryId).containsExactly(115L, 116L);
        assertThat(miss.lastPointGroup()).allSatisfy(e -> {
            assertThat(e.showRank()).isEqualTo(15); assertThat(e.contestPoints()).isEqualTo(1); assertThat(e.ballotPoints()).isEqualTo(1);
        });
        assertThat(ContestStatistics.calculate(fixture(16, List.of(a,b), List.of(18,19)), countries).nearMisses().cases()).isEmpty();
        var c = order(1); var d = order(1); var e = order(1);
        c.set(13,14); c.set(14,15); d.set(13,15); d.set(14,16); e.set(13,16); e.set(14,14);
        var triple = ContestStatistics.calculate(fixture(17,List.of(c,d,e),List.of(18,19,20)), countries);
        assertThat(triple.standings().rows().stream().mapToInt(ContestStandingsResponse.Row::totalPoints).sum()).isEqualTo(143);
        assertThat(triple.nearMisses().cases().getFirst().gap()).isEqualTo(3);
        assertThat(triple.nearMisses().cases().getFirst().lastPointGroup()).hasSize(3).allSatisfy(v -> {
            assertThat(v.showRank()).isEqualTo(14); assertThat(v.ballotPoints()).isEqualTo(3); assertThat(v.contestPoints()).isEqualTo(2);
        });
    }

    @Test void s3bT02KeepsAllFrequencyAndGapTiesRepeatedPeopleAndNoEntryStates() {
        var data = addShow(fixture(18,List.of(order(1)),List.of(19)), 2, List.of(order(1)), List.of(19), true);
        var stats = ContestStatistics.calculate(data, countries);
        assertThat(stats.nearMisses().frequencyWinnerIds()).containsExactly(16L,17L,18L);
        assertThat(stats.nearMisses().frequencies()).allSatisfy(f -> assertThat(f.count()).isEqualTo(2));
        assertThat(stats.nearMisses().smallestGapEntryIds()).containsExactly(116L,117L,118L,216L,217L,218L);
        assertThat(stats.nearMisses().cases()).allSatisfy(c -> assertThat(c.gap()).isEqualTo(1));
        assertThat(stats.nearMisses().frequencyWinnerIds()).doesNotContain(19L,20L);
        var renamed = new ResultData(data.contestId(),data.current(),data.shows(), data.participants().stream()
                .map(p -> new ResultData.Participation(p.id(),p.participantId(),"Same name","AT")).toList(), data.entries(),data.ballots(),data.positions());
        assertThat(ContestStatistics.calculate(renamed,countries).nearMisses()).isEqualTo(stats.nearMisses());
        var reopened = new ResultData(data.contestId(),data.current(),data.shows().stream()
                .map(s -> new ResultData.Show(s.id(),s.number(),s.name(),true,null)).toList(),data.participants(),data.entries(),data.ballots(),data.positions());
        var empty = ContestStatistics.calculate(reopened,countries).nearMisses();
        assertThat(empty.cases()).isEmpty(); assertThat(empty.frequencies()).isEmpty();
        assertThat(empty.frequencyWinnerIds()).isEmpty(); assertThat(empty.smallestGapEntryIds()).isEmpty();
    }

    @Test void s3bT03CreatesAndResolvesSharedWinnersWithTheSameRankKernel() {
        var a = order(1); var b = order(1); b.set(0,2); b.set(1,1);
        var data = fixture(17,List.of(a,b),List.of(18,19));
        var impact = BallotImpact.calculate(data,countries,1,118);
        assertThat(impact.actualWinnerEntryIds()).containsExactly(101L,102L);
        assertThat(impact.hypotheticalWinnerEntryIds()).containsExactly(102L);
        assertThat(comparison(impact,101).actual()).isEqualTo(new BallotImpactResponse.Outcome(45,1,25,25));
        assertThat(comparison(impact,101).hypothetical()).isEqualTo(new BallotImpactResponse.Outcome(20,2,20,20));
        var triple = BallotImpact.calculate(fixture(17,List.of(a,b,a),List.of(18,19,20)),countries,1,120);
        assertThat(triple.actualWinnerEntryIds()).containsExactly(101L);
        assertThat(triple.hypotheticalWinnerEntryIds()).containsExactly(101L,102L);
        assertThat(comparison(triple,102).actual().ballotPoints()).isEqualTo(65);
        assertThat(comparison(triple,101).actual().ballotPoints()).isEqualTo(70);
        assertThat(comparison(triple,102).delta().contestPoints()).isEqualTo(5);
        assertThat(comparison(triple,102).delta().ballotPoints()).isEqualTo(-20);
        assertSums(impact,280,140); assertSums(triple,420,280);
    }

    @Test void s3bT03T04RetainsVoterEntryIndirectChangesAndOtherShowTotals() {
        var data = addShow(fixture(17,List.of(order(1),order(2)),List.of(16,18)),2,List.of(order(2)),List.of(18),true);
        data = addShow(data,3,List.of(order(1)),List.of(18),false);
        var before = ContestStatistics.calculate(data,countries);
        var impact = BallotImpact.calculate(data,countries,1,116);
        var e16 = comparison(impact,116);
        assertThat(e16.actual()).isEqualTo(new BallotImpactResponse.Outcome(1,16,0,1));
        assertThat(e16.hypothetical()).isEqualTo(new BallotImpactResponse.Outcome(1,15,1,2));
        assertThat(e16.delta().ballotPoints()).isZero(); assertThat(e16.delta().showRank()).isEqualTo(1);
        assertThat(e16.boundaryChange()).isEqualTo("ENTERED");
        assertThat(comparison(impact,101).actual().contestTotal()).isEqualTo(13);
        assertThat(comparison(impact,101).hypothetical().contestTotal()).isZero();
        assertThat(comparison(impact,102).actual().contestTotal()).isEqualTo(50);
        assertThat(comparison(impact,102).hypothetical().contestTotal()).isEqualTo(50);
        assertThat(impact.standings().includedShowIds()).containsExactly(1L,2L);
        assertThat(impact.comparisons()).hasSize(17);
        assertThat(ContestStatistics.calculate(data,countries)).isEqualTo(before);
        assertSums(impact,280,140);
    }

    @Test void s3bT04T05DistinguishesSingleBallotAndRejectsInvalidSelections() {
        var data = fixture(17,List.of(order(1)),List.of(18));
        var impact = BallotImpact.calculate(data,countries,1,118);
        assertThat(impact.state()).isEqualTo("NO_REMAINING_BALLOT");
        assertThat(impact.actualWinnerEntryIds()).containsExactly(101L);
        assertThat(impact.hypotheticalWinnerEntryIds()).isEmpty();
        assertThat(impact.comparisons()).allSatisfy(c -> { assertThat(c.hypothetical()).isNull(); assertThat(c.delta()).isNull(); });
        for (long id : List.of(999L,101L)) assertThatThrownBy(() -> BallotImpact.calculate(data,countries,1,id)).isInstanceOf(ApiConflictException.class);
        assertThatThrownBy(() -> BallotImpact.calculate(data,countries,99,118)).isInstanceOf(ApiConflictException.class);
        var open = new ResultData(9,false,List.of(new ResultData.Show(1,1,"Open",true,null)),data.participants(),data.entries(),data.ballots(),data.positions());
        assertThatThrownBy(() -> BallotImpact.calculate(open,countries,1,118)).isInstanceOf(ApiConflictException.class);
        var corrupt = new ResultData(9,false,data.shows(),data.participants(),data.entries(),data.ballots(),data.positions().subList(0,14));
        assertThatThrownBy(() -> BallotImpact.calculate(corrupt,countries,1,118)).isInstanceOf(ApiConflictException.class);
    }

    @Test void s3bT07HundredParticipantsTwelveShowsHaveOnlyOneRequestedSimulation() {
        var people = IntStream.rangeClosed(1,100).mapToObj(i -> new ResultData.Participation(i,i,"Long synthetic name ".repeat(4) + i,"DE")).toList();
        var seed = fixture(100,List.of(order(1),order(1)),List.of(99,100));
        var data = new ResultData(9,false,seed.shows(),people,seed.entries(),seed.ballots(),seed.positions());
        for (int n = 2; n <= 12; n++) data = addShow(data,n,List.of(order(1),order(1)),List.of(99,100),true);
        var stats = ContestStatistics.calculate(data,countries);
        assertThat(stats.nearMisses().cases()).hasSize(85 * 12);
        assertThat(stats.nearMisses().frequencyWinnerIds()).hasSize(85);
        var impact = BallotImpact.calculate(data,countries,12,1299);
        assertThat(impact.comparisons()).hasSize(100);
        assertThat(impact.removedBallot().positions()).hasSize(15);
        assertSums(impact,280,140);
    }

    static ResultData fixture(int entries, List<List<Integer>> orders, List<Integer> voters) {
        var people = IntStream.rangeClosed(1,Math.max(20,entries)).mapToObj(i -> new ResultData.Participation(i,i,"Synthetic " + i,"DE")).toList();
        return addShow(new ResultData(9,false,List.of(),people,List.of(),List.of(),List.of()),1,orders,voters,true,entries);
    }
    private static ResultData addShow(ResultData data, int number, List<List<Integer>> orders, List<Integer> voters, boolean closed) {
        return addShow(data,number,orders,voters,closed,(int) data.entries().stream().filter(e -> e.showId() == 1).count());
    }
    private static ResultData addShow(ResultData data, int n, List<List<Integer>> orders, List<Integer> voters, boolean closed, int count) {
        var shows = new ArrayList<>(data.shows()); shows.add(new ResultData.Show(n,n,"Synthetic " + n,true,closed ? CLOSED : null));
        var entries = new ArrayList<>(data.entries());
        for (int i = 1; i <= count; i++) entries.add(new ResultData.Entry(n * 100L + i,n,(long) i,"Artist " + i,"Song " + n));
        var ballots = new ArrayList<>(data.ballots()); var positions = new ArrayList<>(data.positions());
        for (var person : data.participants()) ballots.add(new ResultData.Ballot(n * 100L + person.id(),n,person.id(),voters.contains((int) person.id()) ? "ABGESTIMMT" : "NICHT_ABGESTIMMT"));
        for (int v = 0; v < voters.size(); v++) for (int r = 1; r <= 15; r++) positions.add(new ResultData.Position(n * 100L + voters.get(v),n * 100L + orders.get(v).get(r - 1),r));
        var result = new ResultData(data.contestId(),data.current(),shows,data.participants(),entries,ballots,positions);
        assertThat(ResultClosureRules.reasons(result,shows.getLast())).isEmpty();
        return result;
    }
    static List<Integer> order(int start) { return new ArrayList<>(IntStream.range(start,start + 15).boxed().toList()); }
    static BallotImpactResponse.Comparison comparison(BallotImpactResponse result, long entryId) { return result.comparisons().stream().filter(c -> c.entryId() == entryId).findFirst().orElseThrow(); }
    private static void assertSums(BallotImpactResponse result, int actual, int alternative) {
        assertThat(result.comparisons().stream().mapToInt(c -> c.actual().ballotPoints()).sum()).isEqualTo(actual);
        assertThat(result.comparisons().stream().mapToInt(c -> c.hypothetical().ballotPoints()).sum()).isEqualTo(alternative);
    }
}
