package de.venomenon.cscxtool.standings;

import static org.assertj.core.api.Assertions.assertThat;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ContestStatisticsTest {
    private final CountryCatalog countries = new CountryCatalog(new ObjectMapper());

    @Test
    void s2T01AndT03DistinguishOpportunitiesAndUseTheIntersectionOracle() {
        var fixture = new Fixture(21);
        fixture.show(1, true); fixture.vote(1, 1, Map.of(2, 1)); fixture.vote(1, 2, Map.of(1, 6));
        fixture.show(2, true); fixture.vote(2, 1, Map.of(2, 2));
        fixture.show(3, true); fixture.vote(3, 1, Map.of()); fixture.vote(3, 2, Map.of(1, 3));
        fixture.show(4, false); fixture.vote(4, 21, Map.of(2, 1));
        fixture.show(5, true); fixture.vote(5, 21, Map.of(2, 1));
        var result = calculate(fixture.data());
        var ab = relation(result, 1, 2);
        assertThat(ab.points()).isEqualTo(45);
        assertThat(ab.opportunities()).isEqualTo(3);
        assertThat(ab.scoredShows()).isEqualTo(2);
        assertThat(ab.average()).isEqualTo(15);
        assertThat(ab.twentyFives()).isEqualTo(1);
        assertThat(ab.shows()).extracting(ContestStatisticsResponse.Evaluation::state)
                .containsExactly("POINTS", "POINTS", "OUTSIDE_TOP_15", "NOT_COUNTED", "NOT_VOTED");
        assertThat(ab.shows().get(2).points()).isZero();
        assertThat(ab.shows().get(2).ballotRank()).isNull();
        var ba = relation(result, 2, 1);
        assertThat(ba.points()).isEqualTo(26);
        assertThat(ba.opportunities()).isEqualTo(2);
        assertThat(ba.average()).isEqualTo(13);
        assertThat(ba.scoredShows()).isEqualTo(2);
        var pair = result.pairs().stream().filter(p -> p.firstId() == 1 && p.secondId() == 2).findFirst().orElseThrow();
        assertThat(pair.commonShowIds()).containsExactly(1L, 3L);
        assertThat(pair.firstToSecond()).isEqualTo(25);
        assertThat(pair.secondToFirst()).isEqualTo(26);
        assertThat(pair.partnership()).isEqualTo(25);
        assertThat(pair.difference()).isEqualTo(1);
        assertThat(pair.strongerGiverId()).isEqualTo(2);
        assertThat(relation(result, 21, 2).points()).isEqualTo(25); // A giver needs no own song.
        assertThat(relation(result, 1, 21).opportunities()).isZero();
        assertThat(relation(result, 1, 21).average()).isNull();
        assertThat(result.relations()).noneMatch(r -> r.giverId() == r.receiverId());
        assertThat(result.pairs()).noneMatch(p -> p.firstId() == 1 && p.secondId() == 21);
        assertThat(result.relations()).allSatisfy(r -> {
            assertThat(r.points()).isEqualTo(r.shows().stream().filter(e -> e.points() != null).mapToInt(ContestStatisticsResponse.Evaluation::points).sum());
            assertThat(r.opportunities()).isEqualTo(r.shows().stream().filter(e -> e.points() != null).count());
        });
    }

    @Test
    void s2T02IncludesEveryPersonalAndGlobalBoundaryTieAndKnownZerosOnlyInFullLists() {
        var fixture = new Fixture(21);
        for (int show = 1; show <= 6; show++) {
            fixture.show(show, true);
            var ranks = new java.util.LinkedHashMap<Integer, Integer>();
            for (int recipient = 1; recipient <= 6; recipient++) ranks.put(recipient, (recipient + show - 2) % 6 + 1);
            fixture.vote(show, 21, ranks);
        }
        var result = calculate(fixture.data());
        var profile = result.profiles().stream().filter(p -> p.participationId() == 21).findFirst().orElseThrow();
        assertThat(profile.topReceiverIds()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L, 6L);
        assertThat(result.topRelations()).hasSize(6);
        assertThat(result.relations().stream().filter(r -> r.opportunities() > 0)).hasSize(20);
        assertThat(result.topRelations()).allSatisfy(d -> assertThat(relation(result, d.giverId(), d.receiverId()).points()).isEqualTo(95));
        var incoming = new Fixture(21);
        incoming.show(1, true);
        for (int giver = 2; giver <= 9; giver++) incoming.vote(1, giver, Map.of(1, 1));
        var incomingResult = calculate(incoming.data());
        assertThat(incomingResult.profiles().stream().filter(p -> p.participationId() == 1).findFirst().orElseThrow().topGiverIds()).hasSize(8);
        var few = new Fixture(21); few.show(1, true); few.vote(1, 21, Map.of());
        assertThat(calculate(few.data()).profiles().stream().filter(p -> p.participationId() == 1).findFirst().orElseThrow().topGiverIds()).hasSize(1);
    }

    @Test
    void s2T03DoesNotAwardZeroPartnershipEqualDifferenceOrMissingBasis() {
        var fixture = new Fixture(21); fixture.show(1, true);
        fixture.vote(1, 1, Map.of()); fixture.vote(1, 2, Map.of());
        var result = calculate(fixture.data());
        assertThat(result.pairs()).hasSize(1);
        assertThat(result.records().partnerships()).isEmpty();
        assertThat(result.records().unrequited()).isEmpty();
        var equal = new Fixture(21); equal.show(1, true);
        equal.vote(1, 1, Map.of(2, 1)); equal.vote(1, 2, Map.of(1, 1));
        assertThat(calculate(equal.data()).records().partnerships()).hasSize(1);
        assertThat(calculate(equal.data()).records().unrequited()).isEmpty();
    }

    @Test
    void s2T04CountsRealRankOneVotesAndEveryMaximalRunWithoutCrossingGaps() {
        var fixture = new Fixture(21);
        for (int show : List.of(1, 2, 3, 4, 5, 7, 8, 9, 10, 11)) {
            fixture.show(show, !List.of(3, 11).contains(show));
            fixture.vote(show, 21, Map.of(1, 1));
        }
        // Closed show 9 without participant 1's entry ends the series too.
        fixture.entries.removeIf(e -> e.showId() == 9 && Long.valueOf(1).equals(e.participationId()));
        fixture.positions.removeIf(p -> p.entryId() == 901);
        // Replace that ballot with a complete one over the remaining songs.
        fixture.positions.removeIf(p -> p.ballotId() == 921);
        fixture.positions.addAll(IntStream.rangeClosed(1, 15).mapToObj(rank -> new ResultData.Position(921, 902 + rank, rank)).toList());
        var result = calculate(fixture.data());
        var profile = result.profiles().stream().filter(p -> p.participationId() == 1).findFirst().orElseThrow();
        assertThat(profile.twentyFives()).isEqualTo(7);
        assertThat(profile.opportunities()).isEqualTo(7);
        assertThat(profile.twentyFiveRate()).isEqualTo(1);
        assertThat(profile.pointRuns()).extracting(ContestStatisticsResponse.Run::showIds)
                .containsExactly(List.of(1L, 2L), List.of(4L, 5L), List.of(7L, 8L));
        assertThat(profile.podiumRuns()).isEqualTo(profile.pointRuns());
        assertThat(profile.top15Count()).isEqualTo(7);
        assertThat(profile.podiumCount()).isEqualTo(7);
        assertThat(profile.wins()).isEqualTo(7);
        assertThat(result.records().twentyFiveParticipantIds()).containsExactly(1L);
        assertThat(result.records().twentyFiveEntryIds()).hasSize(8); // Show 9 awards a different song.
        assertThat(result.entryAwards()).allSatisfy(a -> assertThat(a.twentyFives()).isLessThanOrEqualTo(a.opportunities()));
        fixture.shows.replaceAll(s -> s.id() == 2 ? new ResultData.Show(s.id(), s.number(), s.name(), true, null) : s);
        assertThat(calculate(fixture.data()).profiles().stream().filter(p -> p.participationId() == 1).findFirst().orElseThrow().pointRuns())
                .extracting(ContestStatisticsResponse.Run::showIds).containsExactly(List.of(4L, 5L), List.of(7L, 8L));
    }

    @Test
    void s2T04UsesTiedRanksThreeAndFifteenAndDoesNotInventTwentyFives() {
        var fixture = new Fixture(21); fixture.show(1, true);
        fixture.vote(1, 21, Map.of()); // Songs 3..17 receive ranks 1..15.
        // A second voter balances rank 3 and 4 into a shared show rank 3 (16+13).
        var ranks = new java.util.LinkedHashMap<Integer, Integer>();
        ranks.put(3, 1); ranks.put(4, 2); ranks.put(5, 4); ranks.put(6, 3);
        for (int song = 7; song <= 15; song++) ranks.put(song, song - 2);
        ranks.put(16, 14); ranks.put(18, 15);
        fixture.vote(1, 20, ranks);
        var result = calculate(fixture.data());
        for (long person : List.of(5L, 6L)) {
            var profile = result.profiles().stream().filter(p -> p.participationId() == person).findFirst().orElseThrow();
            assertThat(profile.podiumCount()).isEqualTo(1);
            assertThat(profile.twentyFives()).isZero();
        }
        for (long person : List.of(17L, 18L)) {
            var row = result.standings().rows().stream().filter(r -> r.participationId() == person).findFirst().orElseThrow();
            assertThat(row.shows().getFirst().showRank()).isEqualTo(15);
            assertThat(result.profiles().stream().filter(p -> p.participationId() == person).findFirst().orElseThrow().top15Count()).isEqualTo(1);
        }
    }

    @Test
    void s2T01PreservesStableIdentityOnDisplayChangesAndS2T04HasNoEmptyWinners() {
        var fixture = new Fixture(21); fixture.show(1, true); fixture.vote(1, 21, Map.of(1, 1));
        var before = calculate(fixture.data());
        fixture.people.replaceAll(p -> new ResultData.Participation(p.id(), p.participantId(), "Renamed " + p.id(), "AT"));
        var renamed = calculate(fixture.data());
        assertThat(renamed.relations()).isEqualTo(before.relations());
        assertThat(renamed.profiles()).isEqualTo(before.profiles());
        fixture.shows.replaceAll(s -> new ResultData.Show(s.id(), s.number(), s.name(), true, null));
        var empty = calculate(fixture.data());
        assertThat(empty.topRelations()).isEmpty();
        assertThat(empty.pairs()).isEmpty();
        assertThat(empty.records()).isEqualTo(new ContestStatisticsResponse.Records(List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private ContestStatisticsResponse calculate(ResultData data) { return ContestStatistics.calculate(data, countries); }
    static ContestStatisticsResponse.Relation relation(ContestStatisticsResponse result, long a, long b) {
        return result.relations().stream().filter(r -> r.giverId() == a && r.receiverId() == b).findFirst().orElseThrow();
    }
    /** Complete synthetic 15-position ballots; the last participant deliberately has no song. */
    private static class Fixture {
        final List<ResultData.Participation> people = new ArrayList<>();
        final List<ResultData.Show> shows = new ArrayList<>();
        final List<ResultData.Entry> entries = new ArrayList<>();
        final List<ResultData.Ballot> ballots = new ArrayList<>();
        final List<ResultData.Position> positions = new ArrayList<>();
        Fixture(int size) { for (int i = 1; i <= size; i++) people.add(new ResultData.Participation(i, 1000 + i, "Person " + i, "DE")); }
        void show(int number, boolean closed) {
            shows.add(new ResultData.Show(number, number, "Show " + number, true, closed ? "2026-10-08T00:00:00Z" : null));
            for (int i = 1; i < people.size(); i++) entries.add(new ResultData.Entry(number * 100L + i, number, (long) i, "Artist " + i, "Song " + i));
            for (var p : people) ballots.add(new ResultData.Ballot(number * 100L + p.id(), number, p.id(), "NICHT_ABGESTIMMT"));
        }
        void vote(int show, int giver, Map<Integer, Integer> forced) {
            long ballot = show * 100L + giver;
            ballots.replaceAll(b -> b.id() == ballot ? new ResultData.Ballot(ballot, show, giver, "ABGESTIMMT") : b);
            var used = new java.util.HashSet<>(forced.keySet());
            for (int rank = 1; rank <= 15; rank++) {
                int targetRank = rank;
                int song = forced.entrySet().stream().filter(e -> e.getValue() == targetRank).map(Map.Entry::getKey).findFirst().orElse(0);
                if (song == 0) {
                    song = IntStream.range(3, people.size()).filter(i -> i != giver && !used.contains(i)).findFirst().orElseThrow();
                    used.add(song);
                }
                positions.add(new ResultData.Position(ballot, show * 100L + song, rank));
            }
        }
        ResultData data() { return new ResultData(1, false, shows, people, entries, ballots, positions); }
    }
}
