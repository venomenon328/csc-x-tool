package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Published assignment suffix: {@code Interpret - Titel [Teilnehmer/Land]}. */
final class BracketedAssignmentHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    private static final Pattern ASSIGNMENT = Pattern.compile(
            "^(.*?)\\s*\\[([^\\[\\]/]+)\\s*/\\s*([^\\[\\]/]+)\\]\\s*$"
    );
    private static final ImportWarning MALFORMED_ASSIGNMENT = new ImportWarning(
            "MALFORMED_BRACKET_ASSIGNMENT",
            "Die abschließende Zuordnung in eckigen Klammern ist unvollständig oder mehrdeutig."
    );

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        String value = HistoricalEntryImportText.compact(source.sourceText());
        Matcher assignment = ASSIGNMENT.matcher(value);
        if (assignment.matches()) {
            return parsed(
                    HistoricalEntryImportText.compact(assignment.group(1)),
                    HistoricalEntryImportText.compact(assignment.group(2)),
                    HistoricalEntryImportText.compact(assignment.group(3)),
                    source.directUrl(),
                    List.of()
            );
        }

        int slash = value.lastIndexOf('/');
        if (slash < 0) return Optional.empty();

        int openingBeforeSlash = value.lastIndexOf('[', slash);
        int closingBeforeSlash = value.lastIndexOf(']', slash);
        int closingAfterSlash = value.indexOf(']', slash + 1);
        boolean unmatchedOpening = openingBeforeSlash > closingBeforeSlash;
        boolean trailingClosingWithoutOpening = closingAfterSlash == value.length() - 1 && !unmatchedOpening;
        if (!unmatchedOpening && !trailingClosingWithoutOpening) return Optional.empty();

        Optional<HistoricalEntryImportText.SongParts> song = Optional.empty();
        if (unmatchedOpening && openingBeforeSlash > 0) {
            String songText = HistoricalEntryImportText.compact(value.substring(0, openingBeforeSlash));
            song = HistoricalEntryImportText.songParts(songText);
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.map(HistoricalEntryImportText.SongParts::artist).orElse(null),
                song.map(HistoricalEntryImportText.SongParts::title).orElse(null),
                null, null, source.directUrl(), List.of(MALFORMED_ASSIGNMENT)
        ));
    }

    private static Optional<HistoricalEntryImportParseResult> parsed(
            String songText, String firstToken, String secondToken, String url, List<ImportWarning> warnings
    ) {
        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, firstToken, secondToken, url, List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), firstToken, secondToken, url, warnings
        ));
    }
}
