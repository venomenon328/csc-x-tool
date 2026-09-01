package de.venomenon.cscxtool.tips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

class TipsGameExportTest {

    private final TipsGameRepository repository = mock(TipsGameRepository.class);
    private final TipsGameExportService service = new TipsGameExportService(repository, new CountryCatalog(new ObjectMapper()));

    @Test
    void exportsAllSongsAlphabeticallyUsingGuessesAndTheKnownOwnAssignment() {
        TipsShowFacts facts = facts();
        TipsGame draft = game(TipsGameStatus.DRAFT);
        TipsGame resolved = game(TipsGameStatus.RESOLVED);
        when(repository.findShowFacts(7)).thenReturn(Optional.of(facts));
        when(repository.findGame(7)).thenReturn(Optional.of(draft), Optional.of(resolved));
        when(repository.findParticipants(4)).thenReturn(List.of(
                participant(10, "Rated M", "BR"),
                participant(11, "Peter Neururer", "XL"),
                participant(99, "Eigener User", "PH")
        ));
        when(repository.findEntries(7, 8L)).thenReturn(List.of(
                entry(3, "Beres Hammond", "Rockaway", true, 99L, "Eigener User", "PH", null),
                entry(1, "Atomship", "The Vast Unseen", false, 42L, "Tatsächlicher Fremduser", "CA", 10L),
                entry(2, "Atomship", "A Song", false, 43L, "Noch ein tatsächlicher User", "DE", 11L)
        ));

        String expected = """
                Atomship - A Song [Saarland/Peter Neururer]
                Atomship - The Vast Unseen [Brasilien/Rated M]
                Beres Hammond - Rockaway [Philippinen/Eigener User]""";

        assertThat(service.export(7)).isEqualTo(expected);
        assertThat(service.export(7)).isEqualTo(expected);
    }

    @Test
    void rejectsAnIncompleteStoredTipInsteadOfReturningAPartialExport() {
        when(repository.findShowFacts(7)).thenReturn(Optional.of(facts()));
        when(repository.findGame(7)).thenReturn(Optional.of(game(TipsGameStatus.DRAFT)));
        when(repository.findParticipants(4)).thenReturn(List.of(participant(10, "Rated M", "BR")));
        when(repository.findEntries(7, 8L)).thenReturn(List.of(
                entry(1, "Atomship", "The Vast Unseen", false, 42L, "Tatsächlicher Fremduser", "CA", null)
        ));

        assertThatThrownBy(() -> service.export(7))
                .isInstanceOf(ApiConflictException.class)
                .hasMessageContaining("alle tippbaren Beiträge");
    }

    @Test
    void exposesUtf8PlainTextAsADownload() {
        TipsGameExportService exportService = mock(TipsGameExportService.class);
        when(exportService.export(7)).thenReturn("Ärzte - Lied [Saarland/Peter Neururer]");

        var response = new TipsGameExportController(exportService).export(7);

        assertThat(response.getBody()).isEqualTo("Ärzte - Lied [Saarland/Peter Neururer]");
        assertThat(response.getHeaders().getContentType()).hasToString("text/plain;charset=UTF-8");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment").contains("tippspiel-zuordnungen.txt");
    }

    private static TipsShowFacts facts() {
        return new TipsShowFacts(7, 4, true, 3, 0, 3, 99L, 3L);
    }

    private static TipsGame game(TipsGameStatus status) {
        Instant now = Instant.parse("2026-09-01T10:00:00Z");
        return new TipsGame(8, 7, 4, status, now, now, status == TipsGameStatus.RESOLVED ? now : null);
    }

    private static TipsParticipant participant(long participationId, String displayName, String countryCode) {
        return new TipsParticipant(participationId, participationId + 100, displayName, countryCode, true, true);
    }

    private static TipsEntry entry(long id, String artist, String title, boolean ownEntry,
                                   Long actualParticipationId, String actualDisplayName, String actualCountryCode,
                                   Long guessedParticipationId) {
        return new TipsEntry(id, artist, title, null, actualParticipationId, actualParticipationId,
                actualDisplayName, actualCountryCode, guessedParticipationId == null ? null : id + 1000,
                guessedParticipationId, null, null, ownEntry);
    }
}
