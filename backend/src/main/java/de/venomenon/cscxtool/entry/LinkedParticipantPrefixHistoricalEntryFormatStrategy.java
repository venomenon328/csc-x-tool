package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;

/** Format C: a country/participant prefix followed by one linked song. */
final class LinkedParticipantPrefixHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        if (source.htmlPrefix() != null && source.htmlAnchorText() != null) {
            return fromParts(source.htmlPrefix(), source.htmlAnchorText(), source.directUrl());
        }
        return markdownParts(source.sourceText())
                .flatMap(markdown -> fromParts(markdown.prefix(), markdown.songText(), markdown.url()));
    }

    private static Optional<HistoricalEntryImportParseResult> fromParts(String rawPrefix, String rawSongText, String url) {
        String prefix = HistoricalEntryImportText.removeBoundaryMarkdown(rawPrefix);
        String songText = HistoricalEntryImportText.removeBoundaryMarkdown(rawSongText);
        Optional<HistoricalEntryImportText.SongParts> assignment = HistoricalEntryImportText.songParts(prefix);
        if (assignment.isEmpty()) return Optional.empty();
        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, assignment.get().title(), assignment.get().artist(), url, List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), assignment.get().title(), assignment.get().artist(), url, List.of()
        ));
    }

    private static Optional<MarkdownParts> markdownParts(String rawValue) {
        String value = HistoricalEntryImportText.compact(rawValue);
        int opening = value.indexOf('[');
        int closing = value.indexOf("](", opening + 1);
        if (opening < 1 || closing < opening || value.indexOf('[', opening + 1) >= 0) return Optional.empty();
        int urlEnd = value.indexOf(')', closing + 2);
        if (urlEnd < closing || !value.substring(urlEnd + 1).isBlank()) return Optional.empty();
        String prefix = value.substring(0, opening);
        String songText = value.substring(opening + 1, closing);
        String url = value.substring(closing + 2, urlEnd);
        if (prefix.isBlank() || songText.isBlank() || url.isBlank()) return Optional.empty();
        return Optional.of(new MarkdownParts(prefix, songText, url));
    }

    private record MarkdownParts(String prefix, String songText, String url) { }
}
