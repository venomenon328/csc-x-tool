package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Format A: {@code Interpret - Titel (Land/Teilnehmer)} in either assignment order. */
final class ParentheticalHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    private static final Pattern ASSIGNMENT = Pattern.compile("^(.*?)\\s*\\(([^()/]+)\\s*/\\s*([^()/]+)\\)\\s*$");

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        Matcher assignment = ASSIGNMENT.matcher(HistoricalEntryImportText.compact(source.sourceText()));
        if (!assignment.matches()) return Optional.empty();
        String songText = HistoricalEntryImportText.compact(assignment.group(1));
        String firstToken = HistoricalEntryImportText.compact(assignment.group(2));
        String secondToken = HistoricalEntryImportText.compact(assignment.group(3));
        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, firstToken, secondToken, source.directUrl(), List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), firstToken, secondToken, source.directUrl(), List.of()
        ));
    }
}
