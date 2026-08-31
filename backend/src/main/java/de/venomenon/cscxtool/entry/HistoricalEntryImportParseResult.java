package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.stream.Stream;

/** Structured values returned by one historical import format strategy. */
record HistoricalEntryImportParseResult(
        String artist,
        String title,
        String firstAssignmentToken,
        String secondAssignmentToken,
        String url,
        List<ImportWarning> warnings
) {
    HistoricalEntryImportParseResult {
        warnings = List.copyOf(warnings);
    }

    HistoricalEntryImportParseResult withAdditionalWarnings(List<ImportWarning> additionalWarnings) {
        if (additionalWarnings.isEmpty()) return this;
        return new HistoricalEntryImportParseResult(
                artist, title, firstAssignmentToken, secondAssignmentToken, url,
                Stream.concat(warnings.stream(), additionalWarnings.stream()).toList()
        );
    }
}
