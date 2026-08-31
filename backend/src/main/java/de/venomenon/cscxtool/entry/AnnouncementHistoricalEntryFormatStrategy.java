package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;

/** Format B: {@code Interpret - Titel - Teilnehmer / Land}, split from the right. */
final class AnnouncementHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        String value = HistoricalEntryImportText.compact(source.sourceText());
        int slash = value.lastIndexOf('/');
        if (slash < 1 || slash == value.length() - 1) return Optional.empty();

        String country = HistoricalEntryImportText.compact(value.substring(slash + 1));
        String beforeCountry = HistoricalEntryImportText.compact(value.substring(0, slash));
        int participantSeparatorStart = HistoricalEntryImportText.lastSongSeparatorStart(beforeCountry);
        int participantSeparatorEnd = HistoricalEntryImportText.lastSongSeparatorEnd(beforeCountry);
        if (participantSeparatorStart < 0) return Optional.empty();

        String songText = HistoricalEntryImportText.compact(beforeCountry.substring(0, participantSeparatorStart));
        String participant = HistoricalEntryImportText.compact(beforeCountry.substring(participantSeparatorEnd));
        if (participant.isEmpty() || country.isEmpty()) return Optional.empty();
        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, participant, country, source.directUrl(), List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), participant, country, source.directUrl(), List.of()
        ));
    }
}
