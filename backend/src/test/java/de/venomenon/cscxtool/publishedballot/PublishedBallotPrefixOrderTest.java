package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotPrefixOrderTest {

    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheBindingUgandaFixtureWithOpaqueScoreWordBeforeTheDisplayedNumber() {
        assertReadyFixture(parser.parse("", FIXTURE, participants(), entries(), Set.of()));
    }

    @Test
    void parsesTheUgandaFixtureFromRichHtmlWithNbspAndFormattingBoundaries() {
        assertReadyFixture(parser.parse(richHtml(), FIXTURE, participants(), entries(), Set.of()));
    }

    @Test
    void keepsBareLeadingNumbersDiagnosableInsteadOfGuessingThemIntoSongLines() {
        PublishedBallotPreviewBlock block = parser.parse(
                "", "[#22 Uganda - Berggorilla]\n1", participants(), entries(), Set.of()
        ).getFirst();

        assertThat(block.positions()).isEmpty();
        assertThat(block.warnings()).extracting(BallotImportWarning::code)
                .contains("POSITION_COUNT", "UNRECOGNIZED_POSITION_LINES");
    }

    private static void assertReadyFixture(List<PublishedBallotPreviewBlock> blocks) {
        assertThat(blocks).singleElement().satisfies(block -> {
            assertThat(block.participationId()).isEqualTo(100L);
            assertThat(block.displayName()).isEqualTo("Berggorilla");
            assertThat(block.countryCode()).isEqualTo("UG");
            assertThat(block.status()).isEqualTo("READY");
            assertThat(block.positions()).hasSize(15);
            assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                    .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
            assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                    .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        });
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Berggorilla", "UG"),
                participant(1, "The Red-NGA Shankmos", "NG"),
                participant(2, "Contiomagus", "ZA"),
                participant(3, "Kenny Ospreay", "LU"),
                participant(4, "Scott D'Amore", "BA"),
                participant(5, "Straßenköter", "MX"),
                participant(6, "McKlariato", "CN"),
                participant(7, "Mark Webber", "AU"),
                participant(8, "Clémentine Lyon", "CH"),
                participant(9, "Fletcher Cox", "NR"),
                participant(10, "Kingtoo", "MN"),
                participant(11, "Roman Reigns", "MT"),
                participant(12, "Worm", "DE"),
                participant(13, "Daniel.", "NL"),
                participant(14, "Everton", "GR"),
                participant(15, "snaggletooth", "XS")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode) {
        return new PublishedBallotParticipant(id, 1000 + id, name, countryCode, countryCode, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, 1, "Red Hot Chilli Peppers", "One Way Traffic", "The Red-NGA Shankmos", "NG"),
                entry(2, 2, "Roger Whittaker", "Ein bisschen Aroma", "Contiomagus", "ZA"),
                entry(3, 3, "Linkin Park", "Somewhere I Belong", "Kenny Ospreay", "LU"),
                entry(4, 4, "P!nk", "Get The Party Started", "Scott D'Amore", "BA"),
                entry(5, 5, "Miley Cyrus", "Flowers", "Straßenköter", "MX"),
                entry(6, 6, "Sunstroke Project & Olia Tira", "Run Away", "McKlariato", "CN"),
                entry(7, 7, "The Weeknd", "Blinding Lights", "Mark Webber", "AU"),
                entry(8, 8, "KONGOS", "Come with Me Now", "Clémentine Lyon", "CH"),
                entry(9, 9, "Nik Kershaw", "The Riddle", "Fletcher Cox", "NR"),
                entry(10, 10, "Maitre Gims", "Est-ce que tu m'aimes?", "Kingtoo", "MN"),
                entry(11, 11, "Snoop Dogg & Wiz Khalifa feat. Bruno Mars", "Young, Wild & Free", "Roman Reigns", "MT"),
                entry(12, 12, "R.I.O feat. U-Jean", "Summer Jam", "Worm", "DE"),
                entry(13, 13, "Adam Green", "Emily", "Daniel.", "NL"),
                entry(14, 14, "Elton John", "I'm Still Standing", "Everton", "GR"),
                entry(15, 15, "Gloria Gaynor", "I Will Survive", "snaggletooth", "XS")
        );
    }

    private static PublishedBallotEntry entry(
            long id, long submitterParticipationId, String artist, String title, String submitter, String countryCode
    ) {
        return new PublishedBallotEntry(
                id, 22, artist, title, null, submitterParticipationId, 1000 + submitterParticipationId,
                submitter, countryCode
        );
    }

    private static String richHtml() {
        return FIXTURE.lines().filter(line -> !line.isBlank()).map(line -> {
            if (line.startsWith("[#")) return "<p><strong>" + line + "</strong></p>";
            String formatted = line
                    .replace("*Pointi 16*", "<em>Pointi&nbsp;16</em>")
                    .replace("**Pointi 20**", "<strong>Pointi&nbsp;20</strong>")
                    .replace("***Pointi 25***", "<strong><em>Pointi&nbsp;25</em></strong>");
            if (formatted.startsWith("Pointi ")) formatted = formatted.replaceFirst("Pointi ", "Pointi&nbsp;");
            return "<p>" + formatted + "</p>";
        }).collect(Collectors.joining());
    }

    private static final String FIXTURE = """
            [#22 Uganda - Berggorilla]

            Pointi 1 Nigeria - The Red-NGA Shankmos Red Hot Chilli Peppers - One Way Traffic
            Pointi 2 Südafrika - Contiomagus Roger Whittaker - Ein bisschen Aroma
            Pointi 3 Luxemburg - Kenny Ospreay Linkin Park - Somewhere I Belong
            Pointi 4 Bosnien und Herzegowina - Scott D'Amore P!nk - Get The Party Started
            Pointi 5 Mexiko - Straßenköter Miley Cyrus - Flowers
            Pointi 6 China - McKlariato Sunstroke Project & Olia Tira - Run Away
            Pointi 7 Australien - Mark Webber The Weeknd - Blinding Lights
            Pointi 8 Schweiz - Clémentine Lyon KONGOS - Come with Me Now
            Pointi 9 Nauru - Fletcher Cox Nik Kershaw - The Riddle
            Pointi 10 Mongolei - Kingtoo Maitre Gims - Est-ce que tu m'aimes?
            Pointi 11 Malta - Roman Reigns Snoop Dogg & Wiz Khalifa feat. Bruno Mars - Young, Wild & Free
            Pointi 13 Deutschland - Worm R.I.O feat. U-Jean - Summer Jam
            *Pointi 16* Niederlande - Daniel. Adam Green - Emily
            **Pointi 20** Griechenland - Everton Elton John - I'm Still Standing
            ***Pointi 25*** Schottland - snaggletooth Gloria Gaynor - I Will Survive
            """;
}
