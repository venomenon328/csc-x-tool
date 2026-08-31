package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryImportRepresentationConflictTest {

    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void keepsEquivalentHtmlAndPlaintextRowsVisibleWhenTheirUrlsConflict() {
        String html = "<p><strong>Australien - Mark Webber </strong><a href=\"https://youtu.be/rich-source\">The Weeknd - Blinding Lights</a></p>";
        String text = "**Australien - Mark Webber **[The Weeknd - Blinding Lights](https://youtu.be/plain-source)";
        List<HistoricalImportParticipant> participants = List.of(
                new HistoricalImportParticipant(21, 21, "Mark Webber", "AU", List.of())
        );

        List<HistoricalImportPreviewLine> lines = parser.parse(html, text, participants);

        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(HistoricalImportPreviewLine::youtubeUrl)
                .containsExactlyInAnyOrder("https://youtu.be/rich-source", "https://youtu.be/plain-source");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.WARNING);
            assertThat(line.participantDisplayName()).isEqualTo("Mark Webber");
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("REPRESENTATION_CONFLICT");
        });
    }
}
