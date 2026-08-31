package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntrySpecialCountryResolutionTest {

    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void resolvesCscSpecificCountryNamesThroughTheCentralCatalog() {
        List<HistoricalImportParticipant> participants = List.of(
                participant(1, "EnglandUser", "XE"),
                participant(2, "NorthernUser", "XN"),
                participant(3, "SaarUser", "XL"),
                participant(4, "ScotlandUser", "XS"),
                participant(5, "WalesUser", "XW")
        );

        List<HistoricalImportPreviewLine> lines = parser.parse("", """
                England Band - England Song - EnglandUser / England
                Northern Band - Northern Song - NorthernUser / Nordirland
                Saar Band - Saar Song - SaarUser / Saarland
                Scotland Band - Scotland Song - ScotlandUser / Schottland
                Wales Band - Wales Song - WalesUser / Wales
                """, participants);

        assertThat(lines).hasSize(5);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines).extracting(HistoricalImportPreviewLine::participantDisplayName)
                .containsExactly("EnglandUser", "NorthernUser", "SaarUser", "ScotlandUser", "WalesUser");
        assertThat(lines).extracting(HistoricalImportPreviewLine::countryToken)
                .containsExactly("England", "Nordirland", "Saarland", "Schottland", "Wales");
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of());
    }
}
