package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotNumericEntityTest {

    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheRealWishmasterBallotWithLiteralNumericEntities() {
        List<PublishedBallotPreviewBlock> blocks = parser.parse("", FIXTURE, participants(), entries(), Set.of());

        assertThat(blocks).hasSize(1);
        PublishedBallotPreviewBlock block = blocks.getFirst();
        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.displayName()).isEqualTo("Wishmaster");
        assertThat(block.countryCode()).isEqualTo("KZ");
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
        assertThat(block.positions()).allSatisfy(position -> {
            assertThat(position.sourceText()).contains("ұпай");
            assertThat(position.sourceText()).doesNotContain("&#1201;", "&#1087;", "&#1072;", "&#1081;");
        });
        assertThat(block.warnings()).extracting(BallotImportWarning::code)
                .doesNotContain("POSITION_COUNT", "UNRECOGNIZED_POSITION_LINES", "EXPLICIT_RANK_SEQUENCE");
    }

    @Test
    void decodesLiteralizedNumericEntitiesAfterRichHtmlExtraction() {
        String html = "<div>" + FIXTURE.replace("&", "&amp;").replace("\n", "<br>") + "</div>";

        List<PublishedBallotPreviewBlock> blocks = parser.parse(html, "", participants(), entries(), Set.of());

        assertThat(blocks).hasSize(1);
        PublishedBallotPreviewBlock block = blocks.getFirst();
        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).allSatisfy(position -> {
            assertThat(position.sourceText()).contains("ұпай");
            assertThat(position.sourceText()).doesNotContain("&#1201;");
        });
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Wishmaster", "KZ", "Kasachstan"),
                participant(101, "Die Ente", "VA", "Vatikanstadt"),
                participant(102, "Joshi Judas Zwen", "KP", "Nordkorea"),
                participant(103, "Suicide", "FI", "Finnland"),
                participant(104, "Dr. King Schultz", "KR", "Südkorea"),
                participant(105, "Clémentine Lyon", "FR", "Frankreich"),
                participant(106, "Ravenous", "XS", "Schottland"),
                participant(107, "Grissom", "JP", "Japan"),
                participant(108, "OMW", "WS", "Samoa"),
                participant(109, "Mark Webber", "NO", "Norwegen"),
                participant(110, "Cortez", "NZ", "Neuseeland"),
                participant(111, "McKlariato", "CN", "China"),
                participant(112, "Barney Ross", "CH", "Schweiz"),
                participant(113, "AEWconic", "AU", "Australien"),
                participant(114, "EdgeGF", "BS", "Bahamas"),
                participant(115, "Kingtoo", "IE", "Irland")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode, String countryName) {
        return new PublishedBallotParticipant(id, id + 1_000, name, countryCode, countryName, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, "Corpse", "Miss you", 101, "Die Ente", "VA"),
                entry(2, "BURSTERS", "Colors", 102, "Joshi Judas Zwen", "KP"),
                entry(3, "MakeWar", "Oh, Brother", 103, "Suicide", "FI"),
                entry(4, "Hagane", "WintrySky", 104, "Dr. King Schultz", "KR"),
                entry(5, "The Treatment", "Bite Back", 105, "Clémentine Lyon", "FR"),
                entry(6, "Blues Pills", "Devil Man", 106, "Ravenous", "XS"),
                entry(7, "Dead Sara", "Weatherman", 107, "Grissom", "JP"),
                entry(8, "Wind Rose", "Diggy Diggy Hole", 108, "OMW", "WS"),
                entry(9, "Being As An Ocean", "Alone", 109, "Mark Webber", "NO"),
                entry(10, "Unprocessed", "deadrose", 110, "Cortez", "NZ"),
                entry(11, "Blind Channel", "Dark Side", 111, "McKlariato", "CN"),
                entry(12, "Blues Saraceno", "The River", 112, "Barney Ross", "CH"),
                entry(13, "We Are Temporary", "You Can Now Let Go", 113, "AEWconic", "AU"),
                entry(14, "Vandroya", "Why Should We Say Goodbye", 114, "EdgeGF", "BS"),
                entry(15, "Mattanja Joy Bradley", "Hurricane", 115, "Kingtoo", "IE")
        );
    }

    private static PublishedBallotEntry entry(
            long id, String artist, String title, long submitterParticipationId,
            String submitterName, String submitterCountryCode
    ) {
        return new PublishedBallotEntry(
                id, 1, artist, title, null, submitterParticipationId, submitterParticipationId + 1_000,
                submitterName, submitterCountryCode
        );
    }

    private static final String FIXTURE = """
            [#2 Kasachstan - Wishmaster]
            15. Corpse - Miss you (Vatikanstadt - Die Ente) 1 &#1201;&#1087;&#1072;&#1081;
            14. BURSTERS - Colors (Nordkorea - Joshi Judas Zwen) 2 &#1201;&#1087;&#1072;&#1081;
            13. MakeWar - Oh, Brother (Finnland - Suicide) 3 &#1201;&#1087;&#1072;&#1081;
            12. Hagane - WintrySky (Südkorea - Dr. King Schultz) 4 &#1201;&#1087;&#1072;&#1081;
            11. The Treatment - Bite Back (Frankreich - Clémentine Lyon) 5 &#1201;&#1087;&#1072;&#1081;
            10. Blues Pills - Devil Man (Schottland - Ravenous) 6 &#1201;&#1087;&#1072;&#1081;
            9. Dead Sara - Weatherman (Japan - Grissom) 7 &#1201;&#1087;&#1072;&#1081;
            8. Wind Rose - Diggy Diggy Hole (Samoa - OMW) 8 &#1201;&#1087;&#1072;&#1081;
            7. Being As An Ocean - Alone (Norwegen - Mark Webber) 9 &#1201;&#1087;&#1072;&#1081;
            6. Unprocessed - deadrose (Neuseeland - Cortez) 10 &#1201;&#1087;&#1072;&#1081;
            5. Blind Channel - Dark Side (China - McKlariato) 11 &#1201;&#1087;&#1072;&#1081;

            4. Blues Saraceno - The River (Schweiz - Barney Ross) 13 &#1201;&#1087;&#1072;&#1081;

            3. We Are Temporary - You Can Now Let Go (Australien - AEWconic) 16 &#1201;&#1087;&#1072;&#1081;

            2. Vandroya - Why Should We Say Goodbye (Bahamas - EdgeGF) 20 &#1201;&#1087;&#1072;&#1081;

            1. Mattanja Joy Bradley - Hurricane (Irland - Kingtoo) 25 &#1201;&#1087;&#1072;&#1081;
            """;
}
