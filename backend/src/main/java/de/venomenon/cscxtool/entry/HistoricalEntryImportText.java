package de.venomenon.cscxtool.entry;

import java.net.URI;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small shared text operations; format detection itself remains in the individual strategies. */
final class HistoricalEntryImportText {

    static final Pattern SONG_SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");
    static final Pattern HTTP_URL = Pattern.compile("https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);

    private HistoricalEntryImportText() { }

    static Optional<SongParts> songParts(String value) {
        Matcher separator = SONG_SEPARATOR.matcher(compact(value));
        if (!separator.find()) return Optional.empty();
        String artist = emptyToNull(compact(value.substring(0, separator.start())));
        String title = emptyToNull(compact(value.substring(separator.end())));
        return artist == null || title == null ? Optional.empty() : Optional.of(new SongParts(artist, title));
    }

    static int lastSongSeparatorStart(String value) {
        Matcher separator = SONG_SEPARATOR.matcher(value);
        int position = -1;
        while (separator.find()) position = separator.start();
        return position;
    }

    static int lastSongSeparatorEnd(String value) {
        Matcher separator = SONG_SEPARATOR.matcher(value);
        int position = -1;
        while (separator.find()) position = separator.end();
        return position;
    }

    static boolean looksLikePotentialEntry(String value) {
        return value != null && !value.isBlank() && SONG_SEPARATOR.matcher(value).find();
    }

    static boolean hasMarkdownLink(String value) {
        if (value == null) return false;
        int opening = value.indexOf('[');
        return opening >= 0 && value.indexOf("](", opening + 1) > opening;
    }

    static String compact(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    static String normalized(String value) {
        String normalized = Normalizer.normalize(compact(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        // Exact historical country spelling used by real CSC sources. Keep the raw token for preview/source fidelity;
        // only its lookup identity is aligned with the catalog's German display name.
        return "st. kitts and nevis".equals(normalized) ? "st. kitts und nevis" : normalized;
    }

    static boolean validHttpUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            String scheme = URI.create(compact(value)).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static String removeBoundaryMarkdown(String value) {
        String result = compact(value);
        boolean changed;
        do {
            changed = false;
            for (String marker : new String[] { "**", "__", "~~", "`", "*", "_" }) {
                if (result.startsWith(marker)) {
                    result = compact(result.substring(marker.length()));
                    changed = true;
                }
                if (result.endsWith(marker)) {
                    result = compact(result.substring(0, result.length() - marker.length()));
                    changed = true;
                }
            }
        } while (changed && !result.isEmpty());
        return result;
    }

    static String emptyToNull(String value) { return value.isEmpty() ? null : value; }
    static String trimUrl(String value) { return value.replaceAll("[),.;]+$", ""); }

    record SongParts(String artist, String title) { }
}
