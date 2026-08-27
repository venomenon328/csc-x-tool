package de.venomenon.cscxtool.entry;

import java.util.List;

record ImportPreviewLine(
        int sourcePosition,
        String sourceType,
        String sourceText,
        String artist,
        String title,
        String youtubeUrl,
        ImportPreviewStatus status,
        List<ImportWarning> warnings,
        boolean possibleDuplicate
) {

    ImportPreviewLine withPossibleDuplicate() {
        if (possibleDuplicate) {
            return this;
        }
        return new ImportPreviewLine(
                sourcePosition,
                sourceType,
                sourceText,
                artist,
                title,
                youtubeUrl,
                status == ImportPreviewStatus.INCOMPLETE ? ImportPreviewStatus.INCOMPLETE : ImportPreviewStatus.WARNING,
                appendWarning(new ImportWarning(
                        "POSSIBLE_DUPLICATE",
                        "M\u00f6gliche Dublette: Link oder Interpret und Titel sind bereits vorhanden."
                )),
                true
        );
    }

    private List<ImportWarning> appendWarning(ImportWarning warning) {
        return java.util.stream.Stream.concat(warnings.stream(), java.util.stream.Stream.of(warning)).toList();
    }
}
