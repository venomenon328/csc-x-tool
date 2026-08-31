package de.venomenon.cscxtool.entry;

import java.util.List;

/** A single, untrusted logical clipboard line before a format strategy interprets it. */
record HistoricalImportSourceLine(
        String sourceText,
        ClipboardRepresentation representation,
        String directUrl,
        String htmlPrefix,
        String htmlAnchorText,
        List<ImportWarning> extractionWarnings
) {
    HistoricalImportSourceLine {
        extractionWarnings = List.copyOf(extractionWarnings);
    }

    enum ClipboardRepresentation { RICH_HTML, PLAIN_TEXT }
}
