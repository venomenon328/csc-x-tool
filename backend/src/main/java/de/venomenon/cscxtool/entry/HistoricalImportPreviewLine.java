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
        boolean possibleDuplicate
) {
    HistoricalImportPreviewLine withPossibleDuplicate() {
        if (possibleDuplicate) return this;
        return new HistoricalImportPreviewLine(
                sourcePosition, sourceText, artist, title, youtubeUrl, participantToken, countryToken, participantId,
                participantDisplayName, status == ImportPreviewStatus.INCOMPLETE ? status : ImportPreviewStatus.WARNING,
                java.util.stream.Stream.concat(warnings.stream(), java.util.stream.Stream.of(new ImportWarning(
                        "POSSIBLE_DUPLICATE", "Mögliche Dublette: Dieser Teilnehmer oder Song ist bereits in der Show erfasst."
                ))).toList(), true
        );
    }
}
