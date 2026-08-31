package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotExplicitRankSuffixTest {

    private static final Pattern FIXTURE_LINE = Pattern.compile(
            "^(\\d+)\\.\\s+\\[(.+)]\\((https?://[^)]+)\\)\\s+\\*\\*\\((.+)\\)\\s+\\*\\*(\\d+)\\s+(.+)$"
    );

    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheRealBrazilBallotWithExplicitRanksAndTrailingPortuguesePoints() {
        PublishedBallotPreviewBlock block = parse("", FIXTURE);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.displayName()).isEqualTo("Rated M");
        assertThat(block.countryCode()).isEqualTo("BR");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
        assertThat(block.positions()).allSatisfy(position -> assertThat(position.warnings()).isEmpty());
    }

    @Test
    void treatsTheTrailingDisplayedPointValueAsOpaqueSourceDecoration() {
        String source = FIXTURE.replace("**1 ponto", "**666 pontosimaginarios");

        PublishedBallotPreviewBlock block = parse("", source);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions().getFirst().rank()).isEqualTo(15);
        assertThat(block.positions().getFirst().entryId()).isEqualTo(1);
    }

    @Test
    void rejectsAnExplicitRankSequenceThatDoesNotMatchFifteenDownToOne() {
        String source = FIXTURE.replace(
                "14. [Listener - There's Money in the Walls]",
                "15. [Listener - There's Money in the Walls]"
        );

        PublishedBallotPreviewBlock block = parse("", source);

        assertThat(block.status()).isEqualTo("WARNING");
        assertThat(block.warnings()).extracting(BallotImportWarning::code)
                .contains("EXPLICIT_RANK_SEQUENCE");
    }

    @Test
    void keepsTheCorrectUrlPerLineWhenRichHtmlContainsAllFifteenLinksInOneBlock() {
        PublishedBallotPreviewBlock block = parse(toRichHtml(FIXTURE), "");

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
    }

    private PublishedBallotPreviewBlock parse(String html, String text) {
        List<PublishedBallotPreviewBlock> blocks = parser.parse(html, text, participants(), entries(), Set.of());
        assertThat(blocks).hasSize(1);
        return blocks.getFirst();
    }

    private static String toRichHtml(String fixture) {
        StringBuilder html = new StringBuilder("<div><strong>[#5 Brasilien - Rated M]</strong><br>");
        fixture.lines().skip(1).filter(line -> !line.isBlank()).forEach(line -> {
            Matcher matcher = FIXTURE_LINE.matcher(line);
            assertThat(matcher.matches()).as("fixture line: %s", line).isTrue();
            html.append(matcher.group(1)).append(".&nbsp;<a href=\"")
                    .append(matcher.group(3).replace("&", "&amp;"))
                    .append("\">").append(matcher.group(2)).append("</a>&nbsp;<strong>(")
                    .append(matcher.group(4)).append(")&nbsp;</strong>")
                    .append(matcher.group(5)).append(' ').append(matcher.group(6)).append("<br>");
        });
        return html.append("</div>").toString();
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Rated M", "BR", "Brasilien"),
                participant(101, "Suicide", "FI", "Finnland"),
                participant(102, "Straßenköter", "IS", "Island"),
                participant(103, "Eugene Fan", "BE", "Belgien"),
                participant(104, "Grissom", "JP", "Japan"),
                participant(105, "EdgeGF", "BS", "Bahamas"),
                participant(106, "Toblerone Driver", "TR", "Türkei"),
                participant(107, "Mark Webber", "NO", "Norwegen"),
                participant(108, "Ravenous", "XS", "Schottland"),
                participant(109, "Wishmaster", "KZ", "Kasachstan"),
                participant(110, "OMW", "WS", "Samoa"),
                participant(111, "Kingtoo", "IE", "Irland"),
                participant(112, "Nick Heidfeld", "DE", "Deutschland"),
                participant(113, "Legendk!ller", "IT", "Italien"),
                participant(114, "Barney Ross", "CH", "Schweiz"),
                participant(115, "Clémentine Lyon", "FR", "Frankreich")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode, String countryName) {
        return new PublishedBallotParticipant(id, id + 1_000, name, countryCode, countryName, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, "MakeWar", "Oh, Brother", "https://www.youtube.com/watch?v=CRXgOgFTSh0", 101, "Suicide", "FI"),
                entry(2, "Listener", "There's Money in the Walls", "https://www.youtube.com/watch?v=sCqXflk5-d0", 102, "Straßenköter", "IS"),
                entry(3, "Wally Warning", "No Monkey", "https://www.youtube.com/watch?v=z2Zr3EgzbEs", 103, "Eugene Fan", "BE"),
                entry(4, "Dead Sara", "Weatherman", "https://www.youtube.com/watch?v=y5vr_Vhoumc", 104, "Grissom", "JP"),
                entry(5, "Vandroya", "Why Should We Say Goodbye", "https://www.youtube.com/watch?v=W9pXKIYhTCM", 105, "EdgeGF", "BS"),
                entry(6, "Jack Curley", "I'm Here For You", "https://www.youtube.com/watch?v=EhDUn7q0_l8", 106, "Toblerone Driver", "TR"),
                entry(7, "Being As An Ocean", "Alone", "https://www.youtube.com/watch?v=Fikr0N0htRs", 107, "Mark Webber", "NO"),
                entry(8, "Blues Pills", "Devil Man", "https://youtu.be/5TVyJxBtXy8", 108, "Ravenous", "XS"),
                entry(9, "Spiritbox", "Blessed Be", "https://www.youtube.com/watch?v=yht0WDdzGJM", 109, "Wishmaster", "KZ"),
                entry(10, "Wind Rose", "Diggy Diggy Hole", "https://www.youtube.com/watch?v=34CZjsEI1yU", 110, "OMW", "WS"),
                entry(11, "Mattanja Joy Bradley", "Hurricane", "https://www.youtube.com/watch?v=3ffxz338S8M", 111, "Kingtoo", "IE"),
                entry(12, "Victoria Justice", "Treat Myself", "https://www.youtube.com/watch?v=XSLIgUkrdio", 112, "Nick Heidfeld", "DE"),
                entry(13, "The Roop", "On Fire", "https://www.youtube.com/watch?v=YFzcmH1kDj8", 113, "Legendk!ller", "IT"),
                entry(14, "Blues Saraceno", "The River", "https://www.youtube.com/watch?v=fmLR8S8DYqo", 114, "Barney Ross", "CH"),
                entry(15, "The Treatment", "Bite Back", "https://youtu.be/HsuOhGVmkdg", 115, "Clémentine Lyon", "FR")
        );
    }

    private static PublishedBallotEntry entry(
            long id, String artist, String title, String url, long submitterParticipationId,
            String submitterName, String submitterCountryCode
    ) {
        return new PublishedBallotEntry(
                id, 1, artist, title, url, submitterParticipationId, submitterParticipationId + 1_000,
                submitterName, submitterCountryCode
        );
    }

    private static final String FIXTURE = """
            **[#5 Brasilien - Rated M]**
            15. [MakeWar - Oh, Brother](https://www.youtube.com/watch?v=CRXgOgFTSh0) **(Finnland - Suicide) **1 ponto
            14. [Listener - There's Money in the Walls](https://www.youtube.com/watch?v=sCqXflk5-d0) **(Island - Straßenköter) **2 pontos
            13. [Wally Warning - No Monkey](https://www.youtube.com/watch?v=z2Zr3EgzbEs) **(Belgien - Eugene Fan) **3 pontos
            12. [Dead Sara - Weatherman](https://www.youtube.com/watch?v=y5vr_Vhoumc) **(Japan - Grissom) **4 pontos
            11. [Vandroya - Why Should We Say Goodbye](https://www.youtube.com/watch?v=W9pXKIYhTCM) **(Bahamas - EdgeGF) **5 pontos
            10. [Jack Curley - I'm Here For You](https://www.youtube.com/watch?v=EhDUn7q0_l8) **(Türkei - Toblerone Driver) **6 pontos
            9. [Being As An Ocean - Alone](https://www.youtube.com/watch?v=Fikr0N0htRs) **(Norwegen - Mark Webber) **7 pontos
            8. [Blues Pills - Devil Man](https://youtu.be/5TVyJxBtXy8) **(Schottland - Ravenous) **8 pontos
            7. [Spiritbox – Blessed Be](https://www.youtube.com/watch?v=yht0WDdzGJM) **(Kasachstan - Wishmaster) **9 pontos
            6. [Wind Rose - Diggy Diggy Hole](https://www.youtube.com/watch?v=34CZjsEI1yU) **(Samoa - OMW) **10 pontos
            5. [Mattanja Joy Bradley - Hurricane](https://www.youtube.com/watch?v=3ffxz338S8M) **(Irland - Kingtoo) **11 pontos
            4. [Victoria Justice - Treat Myself](https://www.youtube.com/watch?v=XSLIgUkrdio) **(Deutschland - Nick Heidfeld) **13 pontos
            3. [The Roop - On Fire](https://www.youtube.com/watch?v=YFzcmH1kDj8) **(Italien - Legendk!ller) **16 pontos
            2. [Blues Saraceno - The River](https://www.youtube.com/watch?v=fmLR8S8DYqo) **(Schweiz - Barney Ross) **20 pontos
            1. [The Treatment - Bite Back](https://youtu.be/HsuOhGVmkdg) **(Frankreich - Clémentine Lyon) **25 pontos
            """;
}
