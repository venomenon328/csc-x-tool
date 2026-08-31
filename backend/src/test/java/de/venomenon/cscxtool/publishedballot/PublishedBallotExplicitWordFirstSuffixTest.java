package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotExplicitWordFirstSuffixTest {

    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheRealUgandaBallotWithPointWordBeforeDisplayedValue() {
        PublishedBallotPreviewBlock block = parse(FIXTURE);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.displayName()).isEqualTo("Berggorilla");
        assertThat(block.countryCode()).isEqualTo("UG");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
        assertThat(block.positions()).allSatisfy(position -> assertThat(position.warnings()).isEmpty());
    }

    @Test
    void treatsTheTrailingWordFirstPointValueAsOpaqueSourceDecoration() {
        PublishedBallotPreviewBlock block = parse(FIXTURE.replace("Pointi 1", "Pointi 666"));

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.positions().getFirst().rank()).isEqualTo(15);
        assertThat(block.positions().getFirst().entryId()).isEqualTo(1);
    }

    private PublishedBallotPreviewBlock parse(String source) {
        List<PublishedBallotPreviewBlock> blocks = parser.parse("", source, participants(), entries(), Set.of());
        assertThat(blocks).hasSize(1);
        return blocks.getFirst();
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Berggorilla", "UG", "Uganda"),
                participant(101, "Wishmaster", "KZ", "Kasachstan"),
                participant(102, "Suicide", "FI", "Finnland"),
                participant(103, "McKlariato", "CN", "China"),
                participant(104, "KlötenKlaus", "SE", "Schweden"),
                participant(105, "Daniel.", "NL", "Niederlande"),
                participant(106, "Barney Ross", "CH", "Schweiz"),
                participant(107, "Fingerinpo", "NR", "Nauru"),
                participant(108, "Die Ente", "VA", "Vatikanstadt"),
                participant(109, "Red Forman", "PT", "Portugal"),
                participant(110, "Clémentine Lyon", "FR", "Frankreich"),
                participant(111, "Peter Neururer", "XL", "Saarland"),
                participant(112, "Toblerone Driver", "TR", "Türkei"),
                participant(113, "Nick Heidfeld", "DE", "Deutschland"),
                participant(114, "Legendk!ller", "IT", "Italien"),
                participant(115, "Cameron Grimes", "EG", "Ägypten")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode, String countryName) {
        return new PublishedBallotParticipant(id, id + 1_000, name, countryCode, countryName, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, "Spiritbox", "Blessed Be", 101, "Wishmaster", "KZ"),
                entry(2, "MakeWar", "Oh, Brother", 102, "Suicide", "FI"),
                entry(3, "Blind Channel", "Dark Side", 103, "McKlariato", "CN"),
                entry(4, "Cat Ballou", "Et jitt kein wood", 104, "KlötenKlaus", "SE"),
                entry(5, "Grillmaster Flash", "Sottrum", 105, "Daniel.", "NL"),
                entry(6, "Blues Saraceno", "The River", 106, "Barney Ross", "CH"),
                entry(7, "The Tragic Thrills", "Fever", 107, "Fingerinpo", "NR"),
                entry(8, "Corpse", "Miss you", 108, "Die Ente", "VA"),
                entry(9, "UMME BLOCK", "Yellow Lights", 109, "Red Forman", "PT"),
                entry(10, "The Treatment", "Bite Back", 110, "Clémentine Lyon", "FR"),
                entry(11, "Andreas Kümmert", "Simple Man", 111, "Peter Neururer", "XL"),
                entry(12, "Jack Curley", "I'm Here For You", 112, "Toblerone Driver", "TR"),
                entry(13, "Victoria Justice", "Treat Myself", 113, "Nick Heidfeld", "DE"),
                entry(14, "The Roop", "On Fire", 114, "Legendk!ller", "IT"),
                entry(15, "Mathea", "Chaos", 115, "Cameron Grimes", "EG")
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
            [#30 Uganda - Berggorilla]
            15. Spiritbox – Blessed Be (Kasachstan - Wishmaster) Pointi 1
            14. MakeWar - Oh, Brother (Finnland - Suicide) Pointi 2
            13. Blind Channel - Dark Side (China - McKlariato) Pointi 3
            12. Cat Ballou - Et jitt kein wood (Schweden - KlötenKlaus) Pointi 4
            11. Grillmaster Flash - Sottrum (Niederlande - Daniel.) Pointi 5
            10. Blues Saraceno - The River (Schweiz - Barney Ross) Pointi 6
            9. The Tragic Thrills - Fever (Nauru - Fingerinpo) Pointi 7
            8. Corpse - Miss you (Vatikanstadt - Die Ente) Pointi 8
            7. UMME BLOCK - Yellow Lights (Portugal - Red Forman) Pointi 9
            6. The Treatment - Bite Back (Frankreich - Clémentine Lyon) Pointi 10
            5. Andreas Kümmert - Simple Man (Saarland - Peter Neururer) Pointi 11

            4. Jack Curley - I'm Here For You (Türkei - Toblerone Driver) Pointi 13

            3. Victoria Justice - Treat Myself (Deutschland - Nick Heidfeld) Pointi 16

            2. The Roop - On Fire (Italien - Legendk!ller) Pointi 20

            1. Mathea - Chaos (Ägypten - Cameron Grimes) Pointi 25
            """;
}
