package de.venomenon.cscxtool.entry;

import java.util.List;

/** Untrusted clipboard data, held only for the response that renders an import preview. */
record HistoricalImportPreviewLine(
        int sourcePosition,
        String sourceText,
        String artist,
        String title,
        String youtubeUrl,
        String participantToken,
        String countryToken,
        Long participantId,
        String participantDisplayName,
        ImportPreviewStatus status,
        List<ImportWarning> warnings,
        Long replaceEntryId,
        boolean possibleDuplicate
) {
    HistoricalImportPreviewLine withPossibleDuplicate(Long suggestedReplaceEntryId) {
        if (possibleDuplicate && replaceEntryId != null) return this;
        return new HistoricalImportPreviewLine(
                sourcePosition, sourceText, artist, title, youtubeUrl, participantToken, countryToken, participantId,
                participantDisplayName, status == ImportPreviewStatus.INCOMPLETE ? status : ImportPreviewStatus.WARNING,
                java.util.stream.Stream.concat(warnings.stream(), java.util.stream.Stream.of(new ImportWarning(
                        "POSSIBLE_DUPLICATE", suggestedReplaceEntryId == null
                                ? "Mögliche Dublette: Dieser Teilnehmer oder Song ist bereits in der Show erfasst."
                                : "Für diesen Teilnehmer existiert bereits ein Beitrag. Ein Ersatz ist nur nach bewusster Auswahl möglich."
                ))).toList(), suggestedReplaceEntryId, true
        );
    }
}
