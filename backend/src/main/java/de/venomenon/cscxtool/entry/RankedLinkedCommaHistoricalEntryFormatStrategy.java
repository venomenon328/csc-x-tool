package de.venomenon.cscxtool.entry;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Format E: numbered linked song + comma-separated country/participant assignment + ignored score suffix. */
final class RankedLinkedCommaHistoricalEntryFormatStrategy implements HistoricalEntryImportFormatStrategy {

    private static final Pattern ORDINAL = Pattern.compile("^\\d+\\.$");
    private static final Pattern LEADING_ORDINAL = Pattern.compile("^\\d+\\.\\s+(.+)$");
    private static final Pattern TRAILING_DECORATION = Pattern.compile("^(.+)\\)\\s+[-–—]\\s+(.+)$");

    @Override
    public Optional<HistoricalEntryImportParseResult> parse(HistoricalImportSourceLine source) {
        if (source.htmlAnchorText() != null && source.directUrl() != null) {
            Optional<HistoricalEntryImportParseResult> html = parseRichHtml(source);
            if (html.isPresent()) return html;
        }

        Optional<MarkdownParts> markdown = markdownParts(source.sourceText());
        if (markdown.isPresent()) {
            MarkdownParts parts = markdown.get();
            return fromParts(parts.songText(), parts.assignmentAndDecoration(), parts.url());
        }

        return flatParts(source.sourceText()).flatMap(parts ->
                fromParts(parts.songText(), parts.assignmentAndDecoration(), source.directUrl()));
    }

    private static Optional<HistoricalEntryImportParseResult> parseRichHtml(HistoricalImportSourceLine source) {
        String prefix = HistoricalEntryImportText.compact(source.htmlPrefix());
        if (!prefix.isBlank() && !ORDINAL.matcher(prefix).matches()) return Optional.empty();

        String visible = HistoricalEntryImportText.compact(source.sourceText());
        String anchor = HistoricalEntryImportText.compact(source.htmlAnchorText());
        if (anchor.isBlank()) return Optional.empty();
        int anchorStart = visible.indexOf(anchor);
        if (anchorStart < 0) return Optional.empty();
        String beforeAnchor = HistoricalEntryImportText.compact(visible.substring(0, anchorStart));
        if (!beforeAnchor.equals(prefix)) return Optional.empty();
        String suffix = HistoricalEntryImportText.compact(visible.substring(anchorStart + anchor.length()));
        return fromParts(anchor, suffix, source.directUrl());
    }

    private static Optional<HistoricalEntryImportParseResult> fromParts(
            String rawSongText, String rawAssignmentAndDecoration, String url
    ) {
        String songText = HistoricalEntryImportText.removeBoundaryMarkdown(rawSongText);
        Optional<AssignmentTokens> assignment = assignmentTokens(rawAssignmentAndDecoration);
        if (assignment.isEmpty()) return Optional.empty();
        String country = canonicalCountryToken(assignment.get().country());

        Optional<HistoricalEntryImportText.SongParts> song = HistoricalEntryImportText.songParts(songText);
        if (song.isEmpty()) {
            return Optional.of(new HistoricalEntryImportParseResult(
                    null, null, country, assignment.get().participant(), url,
                    List.of(new ImportWarning(
                            "MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."
                    ))
            ));
        }
        return Optional.of(new HistoricalEntryImportParseResult(
                song.get().artist(), song.get().title(), country, assignment.get().participant(), url, List.of()
        ));
    }

    private static String canonicalCountryToken(String country) {
        return "St. Kitts and Nevis".equalsIgnoreCase(country) ? "St. Kitts und Nevis" : country;
    }

    private static Optional<AssignmentTokens> assignmentTokens(String rawValue) {
        String value = HistoricalEntryImportText.removeBoundaryMarkdown(rawValue);
        Matcher trailing = TRAILING_DECORATION.matcher(value);
        if (!trailing.matches() || HistoricalEntryImportText.compact(trailing.group(2)).isBlank()) return Optional.empty();
        String assignmentWithOpening = HistoricalEntryImportText.compact(trailing.group(1));
        int opening = assignmentWithOpening.lastIndexOf('(');
        if (opening < 0) return Optional.empty();
        String before = HistoricalEntryImportText.compact(assignmentWithOpening.substring(0, opening));
        if (!before.isBlank()) return Optional.empty();
        String assignment = HistoricalEntryImportText.compact(assignmentWithOpening.substring(opening + 1));
        int comma = assignment.indexOf(',');
        if (comma <= 0 || comma != assignment.lastIndexOf(',')) return Optional.empty();
        String country = HistoricalEntryImportText.compact(assignment.substring(0, comma));
        String participant = HistoricalEntryImportText.compact(assignment.substring(comma + 1));
        return country.isBlank() || participant.isBlank()
                ? Optional.empty() : Optional.of(new AssignmentTokens(country, participant));
    }

    private static Optional<MarkdownParts> markdownParts(String rawValue) {
        String value = HistoricalEntryImportText.compact(rawValue);
        Matcher ordinal = LEADING_ORDINAL.matcher(value);
        if (!ordinal.matches()) return Optional.empty();
        value = ordinal.group(1);
        if (!value.startsWith("[")) return Optional.empty();
        int closing = value.indexOf("](", 1);
        if (closing <= 1 || value.indexOf('[', 1) >= 0) return Optional.empty();
        int urlEnd = value.indexOf(')', closing + 2);
        if (urlEnd < 0) return Optional.empty();
        String songText = value.substring(1, closing);
        String url = value.substring(closing + 2, urlEnd).replace("\\&", "&");
        String suffix = HistoricalEntryImportText.compact(value.substring(urlEnd + 1));
        if (songText.isBlank() || url.isBlank() || suffix.isBlank()) return Optional.empty();
        return Optional.of(new MarkdownParts(songText, url, suffix));
    }

    private static Optional<FlatParts> flatParts(String rawValue) {
        String value = HistoricalEntryImportText.compact(rawValue);
        Matcher ordinal = LEADING_ORDINAL.matcher(value);
        if (!ordinal.matches()) return Optional.empty();
        value = ordinal.group(1);

        Matcher trailing = TRAILING_DECORATION.matcher(value);
        if (!trailing.matches() || HistoricalEntryImportText.compact(trailing.group(2)).isBlank()) return Optional.empty();
        String beforeScore = HistoricalEntryImportText.compact(trailing.group(1));
        int assignmentStart = beforeScore.lastIndexOf(" (");
        if (assignmentStart < 0) return Optional.empty();
        String songText = HistoricalEntryImportText.compact(beforeScore.substring(0, assignmentStart));
        String assignment = HistoricalEntryImportText.compact(beforeScore.substring(assignmentStart + 1)) + ") - ignored";
        return songText.isBlank() ? Optional.empty() : Optional.of(new FlatParts(songText, assignment));
    }

    private record MarkdownParts(String songText, String url, String assignmentAndDecoration) { }
    private record FlatParts(String songText, String assignmentAndDecoration) { }
    private record AssignmentTokens(String country, String participant) { }
}
