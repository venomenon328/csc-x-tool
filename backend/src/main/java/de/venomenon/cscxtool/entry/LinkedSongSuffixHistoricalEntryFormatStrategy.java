package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Format D: one linked song followed by a parenthesized country/participant assignment. */
final class LinkedSongSuffixHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    private static final Pattern ASSIGNMENT = Pattern.compile("^\\((.+?)\\s+[-–—]\\s+(.+?)\\)$");

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        if (source.htmlAnchorText() != null && source.directUrl() != null) {
            String visible = HistoricalEntryImportText.compact(source.sourceText());
            String anchor = HistoricalEntryImportText.compact(source.htmlAnchorText());
            if (visible.startsWith(anchor)) {
                return fromParts(anchor, visible.substring(anchor.length()), source.directUrl());
            }
        }
        return markdownParts(source.sourceText())
                .flatMap(markdown -> fromParts(markdown.songText(), markdown.suffix(), markdown.url()));
    }

    private static Optional<HistoricalEntryImportParseResult> fromParts(String rawSongText, String rawSuffix, String url) {
        String songText = HistoricalEntryImportText.removeBoundaryMarkdown(rawSongText);
        String suffix = HistoricalEntryImportText.removeBoundaryMarkdown(rawSuffix);
        Matcher assignment = ASSIGNMENT.matcher(suffix);
        if (!assignment.matches()) return Optional.empty();

        String firstToken = HistoricalEntryImportText.compact(assignment.group(1));
        String secondToken = HistoricalEntryImportText.compact(assignment.group(2));
        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, firstToken, secondToken, url, List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), firstToken, secondToken, url, List.of()
        ));
    }

    private static Optional<MarkdownParts> markdownParts(String rawValue) {
        String value = HistoricalEntryImportText.compact(rawValue);
        if (!value.startsWith("[")) return Optional.empty();
        int closing = value.indexOf("](", 1);
        if (closing <= 1 || value.indexOf('[', 1) >= 0) return Optional.empty();
        int urlEnd = value.indexOf(')', closing + 2);
        if (urlEnd < 0) return Optional.empty();
        String songText = value.substring(1, closing);
        String url = value.substring(closing + 2, urlEnd).replace("\\&", "&");
        String suffix = value.substring(urlEnd + 1);
        if (songText.isBlank() || url.isBlank() || suffix.isBlank()) return Optional.empty();
        return Optional.of(new MarkdownParts(songText, url, suffix));
    }

    private record MarkdownParts(String songText, String url, String suffix) { }
}
