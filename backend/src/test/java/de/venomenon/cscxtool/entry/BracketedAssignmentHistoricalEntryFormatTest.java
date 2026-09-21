package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BracketedAssignmentHistoricalEntryFormatTest {

    private final HistoricalEntryImportParser parser =
            new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void parsesBracketedAssignmentBlocksWithoutDamagingSongText() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", """
                Band A - Song A [Alice Example/Deutschland]
                Band B - Song B [User.Name/St. Lucia]
                Bänd Ü - Don't Stop, Now - Live [Müller-Test/Österreich]
                Band C - Song / Remix - Live [A. Example/Deutschland]
                Band D - Song [Live] [Jane Doe/Neuseeland]
                """, participants());

        assertThat(lines).hasSize(5);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines.get(0)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Band A");
            assertThat(line.title()).isEqualTo("Song A");
            assertThat(line.participantDisplayName()).isEqualTo("Alice Example");
            assertThat(line.countryToken()).isEqualTo("Deutschland");
        });
        assertThat(lines.get(1)).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("User.Name");
            assertThat(line.countryToken()).isEqualTo("St. Lucia");
        });
        assertThat(lines.get(2)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Bänd Ü");
            assertThat(line.title()).isEqualTo("Don't Stop, Now - Live");
            assertThat(line.participantDisplayName()).isEqualTo("Müller-Test");
        });
        assertThat(lines.get(3)).satisfies(line -> {
            assertThat(line.title()).isEqualTo("Song / Remix - Live");
            assertThat(line.participantDisplayName()).isEqualTo("Alice Example");
        });
        assertThat(lines.get(4)).satisfies(line -> {
            assertThat(line.title()).isEqualTo("Song [Live]");
            assertThat(line.participantDisplayName()).isEqualTo("Jane Doe");
        });
    }

    @Test
    void deduplicatesEquivalentRichAndPlainRepresentations() {
        String line = "Band A - Song A [Alice Example/Deutschland]";
        List<HistoricalImportPreviewLine> lines = parser.parse("<p>" + line + "</p>", line, participants());

        assertThat(lines).singleElement().satisfies(parsed -> {
            assertThat(parsed.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(parsed.artist()).isEqualTo("Band A");
            assertThat(parsed.title()).isEqualTo("Song A");
            assertThat(parsed.participantDisplayName()).isEqualTo("Alice Example");
        });
    }

    @Test
    void keepsMalformedBracketedAssignmentsVisibleInsteadOfFallingThroughToAnnouncementFormat() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", """
                Band A - Song A [Alice Example/Deutschland
                Band B - Song B Alice Example/Deutschland]
                """, participants());

        assertThat(lines).hasSize(2);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.participantId()).isNull();
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).containsExactly("MALFORMED_BRACKET_ASSIGNMENT");
        });
        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Band A");
            assertThat(line.title()).isEqualTo("Song A");
        });
        assertThat(lines.get(1)).satisfies(line -> {
            assertThat(line.artist()).isNull();
            assertThat(line.title()).isNull();
        });
    }

    @Test
    void doesNotStealMarkdownLinksAndKeepsExistingAssignmentFormatsWorking() {
        List<HistoricalImportPreviewLine> markdown = parser.parse(
                "", "**Deutschland - Alice Example **[Band A - Song A](https://youtu.be/aaaaaaaaaaa)", participants()
        );
        List<HistoricalImportPreviewLine> existing = parser.parse("", """
                Band A - Song A (Deutschland/Alice Example)
                Band B - Song B - User.Name / St. Lucia
                """, participants());

        assertThat(markdown).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/aaaaaaaaaaa");
            assertThat(line.participantDisplayName()).isEqualTo("Alice Example");
        });
        assertThat(existing).hasSize(2);
        assertThat(existing).allSatisfy(line -> assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY));
        assertThat(existing).extracting(HistoricalImportPreviewLine::participantDisplayName)
                .containsExactly("Alice Example", "User.Name");
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Alice Example", "DE", "A. Example"),
                participant(2, "User.Name", "LC"),
                participant(3, "Müller-Test", "AT"),
                participant(4, "Jane Doe", "NZ")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode, String... aliases) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of(aliases));
    }
}
