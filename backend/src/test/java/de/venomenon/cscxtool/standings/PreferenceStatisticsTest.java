package de.venomenon.cscxtool.standings;

import static org.assertj.core.api.Assertions.assertThat;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.CscPoints;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import static de.venomenon.cscxtool.standings.PreferenceStatisticsResponse.*;

class PreferenceStatisticsTest {
    private final CountryCatalog countries = new CountryCatalog(new ObjectMapper());

    @Test void s3aT01AlgebraicOverlapAndDefensiveDenominators() {
        assertThat(PreferenceStatistics.overlap(new int[]{25,20}, new int[]{20,25}, false)).isEqualTo(ExactRatio.of(4,5));
        assertThat(PreferenceStatistics.overlap(new int[]{25,20,0}, new int[]{20,25,0}, false)).isEqualTo(ExactRatio.of(4,5));
        assertThat(PreferenceStatistics.overlap(new int[]{25,0}, new int[]{0,25}, false)).isEqualTo(ExactRatio.of(0,1));
        assertThat(PreferenceStatistics.overlap(new int[]{25,20}, new int[]{25,20}, false)).isEqualTo(ExactRatio.of(1,1));
        assertThat(PreferenceStatistics.overlap(new int[0], new int[0], false)).isNull();
        assertThat(PreferenceStatistics.overlap(new int[]{0}, new int[]{0}, false)).isNull();
    }

    @Test void s3aT01CompleteBallotsExcludeBothOwnSongsAndKeepVotersWithoutEntries() {
        var f = new Fixture(40); f.show(1, true);
        f.vote(1, 1, prepend(2, range(3,16))); f.vote(1, 2, prepend(1, range(3,16)));
        var p = pair(f.calculate(), 1, 2);
        assertMetric(p.similarity(), 1, 1);
        assertThat(p.shows().getFirst().excludedEntryIds()).containsExactly(1001L,1002L);
        assertThat(p.shows().getFirst().comparisonEntries()).isEqualTo(37);
        var swapped = new Fixture(40); swapped.show(1,true);
        swapped.vote(1, 1, range(3,17));
        int[] reversedTop = range(3,17); reversedTop[0] = 4; reversedTop[1] = 3;
        swapped.vote(1, 40, reversedTop); // 40 deliberately has no entry.
        var result = swapped.calculate();
        assertMetric(pair(result,1,40).similarity(),27,29);
        assertThat(result.pairs()).filteredOn(v -> v.firstId() == 1 && v.secondId() == 40).hasSize(1);
        assertThat(pair(result,1,2).similarity().value()).isNull();
        assertThat(pair(result,1,2).shows().getFirst().similarity().reason()).contains("Abgabe");
    }

    @Test void s3aT02EqualShowWeightsExactTiesAndMeasuredZeroRecords() {
        assertThat(ExactRatio.of(1,3)).isEqualTo(ExactRatio.of(2,6));
        assertThat(ExactRatio.mean(List.of(ExactRatio.of(1,3),ExactRatio.of(1,6)))).isEqualTo(ExactRatio.of(1,4));
        assertThat(ExactRatio.of(1,3).compareTo(ExactRatio.of(3333,10000))).isPositive();
        var third = PreferenceStatistics.metric(ExactRatio.of(1,3), "");
        var equivalent = PreferenceStatistics.metric(ExactRatio.of(2,6), "");
        var roundedLookalike = PreferenceStatistics.metric(ExactRatio.of(3333,10000), "");
        assertThat(PreferenceStatistics.winners(List.of(roundedLookalike, third, equivalent), java.util.function.Function.identity(), true))
                .containsExactly(third, equivalent);
        var huge = new ExactRatio(BigInteger.TEN.pow(60), BigInteger.TEN.pow(60).multiply(BigInteger.valueOf(3)));
        assertThat(huge).isEqualTo(ExactRatio.of(1,3));
        var f = new Fixture(40);
        f.show(1,true); f.vote(1,1,prepend(2,range(3,16))); f.vote(1,2,prepend(1,range(3,16)));
        f.show(2,true); f.vote(2,1,range(3,17)); f.vote(2,2,range(18,32));
        f.show(3,true); f.vote(3,1,range(3,17)); // Missing B is not zero.
        f.show(4,false); f.vote(4,1,range(3,17)); f.vote(4,2,range(3,17));
        var p = pair(f.calculate(),1,2);
        assertMetric(p.similarity(),1,2);
        assertThat(p.comparedShows()).isEqualTo(2);
        assertThat(p.shows()).extracting(s -> s.similarity().value()).containsExactly(1.0,0.0,null,null);
        var zero = new Fixture(40); zero.show(1,true); zero.vote(1,1,range(3,17)); zero.vote(1,2,range(18,32));
        assertThat(zero.calculate().records().twins()).hasSize(1);
        assertThat(zero.calculate().records().parallels()).isEqualTo(zero.calculate().records().twins());
        var tie = new Fixture(40); tie.show(1,true);
        for (int voter : List.of(1,2,40)) tie.vote(1,voter,range(3,17));
        assertThat(tie.calculate().records().twins()).hasSize(3);
        assertThat(tie.calculate().records().parallels()).hasSize(3);
    }

    @Test void s3aT03OnePopulationForAudienceHistogramVarianceAndTwentyFives() {
        var f = new Fixture(40); f.show(1,true);
        f.vote(1,1,prepend(5,range(16,29)));
        f.vote(1,2,append(range(16,29),5));
        f.vote(1,3,range(16,30));
        f.vote(1,5,range(16,30)); // Self is absent, not a fourth zero.
        var all = f.full(); var e = entry(all.preferences(),1005);
        assertThat(e.evaluations()).isEqualTo(3);
        assertThat(e.positiveEvaluations()).isEqualTo(2);
        assertThat(e.sumPoints()).isEqualTo(26);
        assertMetric(e.audienceRate(),2,3);
        assertThat(e.histogram()).extracting(HistogramBin::points).containsExactly(0,1,2,3,4,5,6,7,8,9,10,11,13,16,20,25);
        assertThat(e.histogram().stream().mapToInt(HistogramBin::count).sum()).isEqualTo(3);
        var award = all.entryAwards().stream().filter(v -> v.entryId() == 1005).findFirst().orElseThrow();
        assertThat(e.twentyFives()).isEqualTo(award.twentyFives());
        assertThat(e.evaluations()).isEqualTo(award.opportunities());
        var variance = new Fixture(40); variance.show(1,true);
        variance.vote(1,1,prepend(5,range(16,29))); variance.vote(1,2,range(16,30));
        var v = entry(variance.calculate(),1005);
        assertMetric(v.variance(),625,4);
        assertThat(v.standardDeviation()).isEqualTo(12.5);
        assertThat(v.polarizationEligible()).isTrue();
        var one = new Fixture(40); one.show(1,true); one.vote(1,1,range(3,17));
        var result = one.calculate();
        assertThat(entry(result,1001).variance().value()).isNull();
        assertMetric(entry(result,1003).variance(),0,1);
        assertThat(entry(result,1003).polarizationEligible()).isFalse();
        assertThat(result.records().polarizationEntryIds()).isEmpty();
        var equal = new Fixture(40); equal.show(1,true); equal.vote(1,1,range(3,17)); equal.vote(1,2,range(3,17));
        assertMetric(entry(equal.calculate(),1003).variance(),0,1);
        assertThat(equal.calculate().records().polarizationEntryIds()).contains(1003L,1004L);
    }

    @Test void s3aT04ExclusivePointsUseTheWholeClosedShowIncludingSingleEvaluations() {
        var f = new Fixture(40); f.show(1,true);
        int[] own = range(16,30); own[0] = 5; own[3] = 6;
        f.vote(1,1,own); f.vote(1,2,range(16,30));
        var p = participant(f.calculate(),1);
        assertThat(p.exclusivePoints()).isEqualTo(38);
        assertThat(p.exclusiveEntryIds()).containsExactly(1005L,1006L);
        assertThat(p.exclusiveTwentyFiveEntryIds()).containsExactly(1005L);
        f.vote(1,3,append(range(16,29),5));
        assertThat(participant(f.calculate(),1).exclusivePoints()).isEqualTo(13);
        assertThat(entry(f.calculate(),1005).exclusiveGiverId()).isNull();
        var single = new Fixture(40); single.show(1,true); single.vote(1,1,range(3,17));
        assertThat(entry(single.calculate(),1003).evaluations()).isEqualTo(1);
        assertThat(participant(single.calculate(),1).exclusivePoints()).isEqualTo(140);
        var open = new Fixture(40); open.show(1,false); open.vote(1,1,range(3,17));
        assertThat(open.calculate().records().exclusiveParticipantIds()).isEmpty();
        assertThat(open.calculate().entries()).isEmpty();
        var common = new Fixture(40); common.show(1,true); common.vote(1,1,range(3,17)); common.vote(1,2,range(3,17));
        assertThat(common.calculate().records().exclusiveParticipantIds()).isEmpty();
    }

    @Test void s3aT05ConsensusRemovesSelfBeforeSummingAndThenNormalizesTheField() {
        assertThat(PreferenceStatistics.overlap(new int[]{25,20},new int[]{50,40},true)).isEqualTo(ExactRatio.of(1,1));
        assertThat(PreferenceStatistics.overlap(new int[]{25,20},new int[]{150,120},true)).isEqualTo(ExactRatio.of(1,1));
        assertThat(PreferenceStatistics.overlap(new int[]{25,0},new int[]{0,25},true)).isEqualTo(ExactRatio.of(0,1));
        assertThat(PreferenceStatistics.overlap(new int[]{25},new int[]{0},true)).isNull();
        var f = new Fixture(40); f.show(1,true); f.vote(1,1,range(3,17)); f.vote(1,2,range(18,32));
        var p = participant(f.calculate(),1);
        assertMetric(p.consensus(),0,1); // Including self incorrectly gives 1/3.
        assertThat(p.shows().getFirst().otherBallots()).isEqualTo(1);
        assertThat(p.shows().getFirst().excludedEntryId()).isEqualTo(1001);
        assertThat(p.shows().getFirst().ownPointSum()).isEqualTo(140);
        assertThat(p.shows().getFirst().fieldPointSum()).isEqualTo(140);
        var own = new Fixture(40); own.show(1,true); own.vote(1,1,range(3,17)); own.vote(1,2,prepend(1,range(3,16)));
        assertThat(participant(own.calculate(),1).shows().getFirst().fieldPointSum()).isEqualTo(115);
        var before = participant(own.calculate(),1).consensus();
        own.vote(1,40,prepend(1,range(3,16)));
        assertThat(participant(own.calculate(),1).consensus()).isEqualTo(before);
        var equal = new Fixture(40); equal.show(1,true); equal.vote(1,1,range(3,17)); equal.vote(1,2,range(3,17));
        equal.show(2,true); equal.vote(2,1,range(3,17)); equal.vote(2,2,range(18,32));
        equal.show(3,true); equal.vote(3,1,range(3,17));
        assertMetric(participant(equal.calculate(),1).consensus(),1,2);
        assertThat(participant(equal.calculate(),1).comparedShows()).isEqualTo(2);
        assertThat(participant(equal.calculate(),1).shows().get(2).similarity().value()).isNull();
        assertThat(participant(equal.calculate(),3).consensus().value()).isNull();
    }

    @Test void s3aT07OneHundredVotersTwelveShowsShareEvidenceAndPreserveEveryTie() {
        var f = new Fixture(100);
        for (int s = 1; s <= 12; s++) {
            f.show(s,true);
            for (int voter = 1; voter <= 100; voter++) {
                int v = voter;
                f.vote(s,voter,IntStream.rangeClosed(1,99).filter(i -> i != v).limit(15).toArray());
            }
        }
        var result = f.calculate();
        assertThat(result.pairs()).hasSize(4950);
        assertThat(result.pairs()).allSatisfy(p -> assertThat(p.comparedShows()).isEqualTo(12));
        assertThat(result.showBases()).hasSize(12).allSatisfy(s -> assertThat(s.ballots()).hasSize(100));
        assertThat(result.showBases().stream().flatMap(s -> s.ballots().stream()).flatMap(b -> b.positions().stream()).count()).isEqualTo(18000);
        assertThat(result.entries().stream().mapToInt(EntryPreference::sumPoints).sum()).isEqualTo(1200 * 140);
        assertThat(result.entries()).allSatisfy(e -> assertThat(e.histogram().stream().mapToInt(HistogramBin::count).sum()).isEqualTo(e.evaluations()));
        // 3570 pairs among voters 16..100 plus 15 adjacent-own-entry pairs have exact similarity 1.
        assertThat(result.records().twins()).hasSize(85 * 84 / 2 + 15);
        assertThat(result.parallelOrder()).hasSize(4950);
        assertThat(result.participants()).hasSize(100);
    }

    private static void assertMetric(Metric metric, long n, long d) {
        var expected = ExactRatio.of(n,d);
        assertThat(metric.numerator()).isEqualTo(expected.numerator().toString());
        assertThat(metric.denominator()).isEqualTo(expected.denominator().toString());
        assertThat(metric.value()).isEqualTo(expected.value());
        assertThat(metric.reason()).isNull();
    }
    static SimilarityPair pair(PreferenceStatisticsResponse p, long a, long b) { return p.pairs().stream().filter(v -> v.firstId() == a && v.secondId() == b).findFirst().orElseThrow(); }
    static EntryPreference entry(PreferenceStatisticsResponse p, long id) { return p.entries().stream().filter(v -> v.entryId() == id).findFirst().orElseThrow(); }
    static ParticipantPreference participant(PreferenceStatisticsResponse p, long id) { return p.participants().stream().filter(v -> v.participationId() == id).findFirst().orElseThrow(); }
    private static int[] range(int a, int b) { return IntStream.rangeClosed(a,b).toArray(); }
    private static int[] prepend(int a, int[] rest) { return IntStream.concat(IntStream.of(a),Arrays.stream(rest)).toArray(); }
    private static int[] append(int[] rest, int a) { return IntStream.concat(Arrays.stream(rest),IntStream.of(a)).toArray(); }
    private class Fixture {
        final List<ResultData.Participation> people = new ArrayList<>();
        final List<ResultData.Show> shows = new ArrayList<>();
        final List<ResultData.Entry> entries = new ArrayList<>();
        final List<ResultData.Ballot> ballots = new ArrayList<>();
        final List<ResultData.Position> positions = new ArrayList<>();
        Fixture(int n) { for (int i = 1; i <= n; i++) people.add(new ResultData.Participation(i,1000 + i,"Long synthetic name " + i,"DE")); }
        void show(int s, boolean closed) {
            shows.add(new ResultData.Show(s,s,"Show " + s,true,closed ? "2026-10-08T00:00:00Z" : null));
            for (var p : people) {
                if (p.id() < people.size()) entries.add(new ResultData.Entry(s * 1000L + p.id(),s,p.id(),"Artist " + p.id(),"Song " + p.id()));
                ballots.add(new ResultData.Ballot(s * 1000L + p.id(),s,p.id(),"NICHT_ABGESTIMMT"));
            }
        }
        void vote(int s, int voter, int[] songs) {
            assertThat(songs).hasSize(15);
            assertThat(Arrays.stream(songs).distinct().count()).isEqualTo(15);
            long id = s * 1000L + voter;
            ballots.replaceAll(b -> b.id() == id ? new ResultData.Ballot(id,s,voter,"ABGESTIMMT") : b);
            positions.removeIf(p -> p.ballotId() == id);
            for (int i = 0; i < 15; i++) positions.add(new ResultData.Position(id,s * 1000L + songs[i],i + 1));
            assertThat(IntStream.rangeClosed(1,15).map(CscPoints::pointsForRank).sum()).isEqualTo(140);
        }
        ContestStatisticsResponse full() {
            var d = new ResultData(1,false,shows,people,entries,ballots,positions);
            ResultClosureRules.validateClosed(d);
            return ContestStatistics.calculate(d,countries);
        }
        PreferenceStatisticsResponse calculate() { return full().preferences(); }
    }
}
