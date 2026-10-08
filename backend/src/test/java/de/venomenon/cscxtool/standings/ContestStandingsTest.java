package de.venomenon.cscxtool.standings;

import static org.assertj.core.api.Assertions.assertThat;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.CompetitionRanks;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ContestStandingsTest {
    @Test
    void awardsEntireTiedGroupsIncludingBeyondFifteenRows() {
        assertPoints(List.of(100, 100, 90), List.of(1, 1, 3), List.of(25, 25, 16));
        var totals = new ArrayList<Integer>();
        for (int i = 0; i < 14; i++) totals.add(100 - i);
        totals.addAll(List.of(10, 10, 0));
        var ranks = CompetitionRanks.of(indexed(totals));
        assertThat(ranks.values()).endsWith(15, 15, 17);
        assertThat(ranks.values().stream().map(CompetitionRanks::contestPoints)).endsWith(1, 1, 0);
        totals.remove(13);
        totals.add(13, 10);
        ranks = CompetitionRanks.of(indexed(totals));
        assertThat(ranks.values()).endsWith(14, 14, 14, 17);
        assertThat(ranks.values().stream().map(CompetitionRanks::contestPoints)).endsWith(2, 2, 2, 0);
    }

    @Test
    void keepsContestTiesGapsNoEntryAndEmptyRanksDistinct() {
        var shows = List.of(new ResultData.Show(10, 1, "First", true, "2026-10-08T00:00:00Z"),
                new ResultData.Show(20, 2, "Open", false, null), new ResultData.Show(30, 4, "Last", true, "2026-10-08T00:00:00Z"));
        var people = List.of(new ResultData.Participation(1, 91, "Zulu", "DE"), new ResultData.Participation(2, 92, "Alpha", "AT"),
                new ResultData.Participation(3, 93, "Without song", "GB"));
        var entries = List.of(new ResultData.Entry(11, 10, 1L, "A", "A"), new ResultData.Entry(12, 10, 2L, "B", "B"),
                new ResultData.Entry(31, 30, 1L, "C", "C"), new ResultData.Entry(32, 30, 2L, "D", "D"));
        var ballots = List.of(new ResultData.Ballot(100, 10, 3, "ABGESTIMMT"), new ResultData.Ballot(300, 30, 3, "ABGESTIMMT"));
        var positions = List.of(new ResultData.Position(100, 11, 1), new ResultData.Position(100, 12, 2),
                new ResultData.Position(300, 31, 2), new ResultData.Position(300, 32, 1));
        var data = new ResultData(9, false, shows, people, entries, ballots, positions);
        var countries = new CountryCatalog(new ObjectMapper());
        var result = ContestStandings.calculate(data, countries);
        assertThat(result.includedShowIds()).containsExactly(10L, 30L);
        assertThat(result.rows()).extracting(ContestStandingsResponse.Row::rank).containsExactly(1, 1, 3);
        assertThat(result.rows()).extracting(ContestStandingsResponse.Row::totalPoints).containsExactly(45, 45, 0);
        var alpha = result.rows().get(1);
        assertThat(alpha.rankChange()).isEqualTo(1);
        assertThat(alpha.history()).extracting(ContestStandingsResponse.Step::rank).containsExactly(2, null, 1);
        assertThat(alpha.history().getFirst().rankChange()).isNull();
        assertThat(result.rows().get(2).shows()).extracting(ContestStandingsResponse.Cell::state).containsExactly("NO_ENTRY", "NOT_COUNTED", "NO_ENTRY");
        var opened = new ResultData(9, false, shows.stream().map(s -> new ResultData.Show(s.id(), s.number(), s.name(), true, null)).toList(), people, entries, ballots, positions);
        assertThat(ContestStandings.calculate(opened, countries).rows()).allSatisfy(r -> assertThat(r.rank()).isNull());
    }

    private static LinkedHashMap<Integer, Integer> indexed(List<Integer> values) {
        var result = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < values.size(); i++) result.put(i, values.get(i));
        return result;
    }
    private static void assertPoints(List<Integer> totals, List<Integer> expectedRanks, List<Integer> expectedPoints) {
        var ranks = CompetitionRanks.of(indexed(totals));
        assertThat(ranks.values()).containsExactlyElementsOf(expectedRanks);
        assertThat(ranks.values().stream().map(CompetitionRanks::contestPoints)).containsExactlyElementsOf(expectedPoints);
    }
}
