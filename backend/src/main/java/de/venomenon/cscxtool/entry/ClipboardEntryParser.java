package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/** Parses untrusted clipboard fragments only into plain preview data; it never fetches or stores the source. */
@Component
class ClipboardEntryParser {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("^\\s*\\[([^]]+)]\\((https?://[^\\s)]+)\\)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");

    private final YoutubeUrlNormalizer youtubeUrlNormalizer;

    ClipboardEntryParser(YoutubeUrlNormalizer youtubeUrlNormalizer) {
        this.youtubeUrlNormalizer = youtubeUrlNormalizer;
    }

    List<ImportPreviewLine> parse(String html, String text) {
        List<SourceLine> sourceLines = new ArrayList<>();
        appendHtmlLines(html, sourceLines);
        appendPlainTextLines(text, sourceLines);

        List<ImportPreviewLine> result = new ArrayList<>();
        Set<String> htmlIdentities = new HashSet<>();
        Set<String> htmlLabels = new HashSet<>();
        for (int index = 0; index < sourceLines.size(); index++) {
            SourceLine sourceLine = sourceLines.get(index);
            ImportPreviewLine line = toPreviewLine(index + 1, sourceLine);
            if (line == null) {
                continue;
            }
            String identity = identity(line);
            String label = normalize(sourceLine.label());
            if (!sourceLine.html() && ((identity != null && htmlIdentities.contains(identity)) || htmlLabels.contains(label))) {
                continue;
            }
            if (sourceLine.html()) {
                if (identity != null) {
                    htmlIdentities.add(identity);
                }
                htmlLabels.add(label);
            }
            result.add(line);
        }
        return List.copyOf(result);
    }

    private static void appendHtmlLines(String html, List<SourceLine> sourceLines) {
        if (html == null || html.isBlank()) {
            return;
        }
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script, style, noscript, template").remove();
        for (Element link : document.select("a[href]")) {
            String label = normalize(link.text());
            String href = normalize(link.attr("href"));
            if (!label.isEmpty() || !href.isEmpty()) {
                sourceLines.add(new SourceLine("HTML_LINK", label, href, true));
            }
        }

        boolean hasLinks = !document.select("a[href]").isEmpty();
        for (Element block : document.select("p, li, div")) {
            if (!block.select("a[href]").isEmpty() || !block.select("p, li, div").isEmpty()) {
                continue;
            }
            appendCandidateTextLines(block.text(), "HTML_TEXT", true, sourceLines);
        }
        if (!hasLinks && document.select("p, li, div").isEmpty()) {
            appendCandidateTextLines(document.body().text(), "HTML_TEXT", true, sourceLines);
        }
    }

    private static void appendPlainTextLines(String text, List<SourceLine> sourceLines) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String rawLine : text.replace('\u00A0', ' ').split("\\R")) {
            String line = normalize(rawLine);
            if (line.isEmpty()) {
                continue;
            }
            Matcher markdown = MARKDOWN_LINK.matcher(line);
            if (markdown.matches()) {
                sourceLines.add(new SourceLine("MARKDOWN_LINK", normalize(markdown.group(1)), normalize(markdown.group(2)), false));
                continue;
            }

            Matcher urlMatcher = URL.matcher(line);
            List<String> urls = new ArrayList<>();
            int firstUrlStart = -1;
            while (urlMatcher.find()) {
                if (firstUrlStart < 0) {
                    firstUrlStart = urlMatcher.start();
                }
                urls.add(trimTrailingPunctuation(urlMatcher.group()));
            }
            if (urls.size() == 1) {
                String label = normalize(line.substring(0, firstUrlStart).replaceAll("(?:->|→)\\s*$", ""));
                sourceLines.add(new SourceLine("PLAINTEXT_URL", label, urls.getFirst(), false));
            } else if (urls.size() > 1 || looksLikeCandidate(line)) {
                sourceLines.add(new SourceLine("PLAINTEXT", line, null, false));
            }
        }
    }

    private static void appendCandidateTextLines(String text, String sourceType, boolean html, List<SourceLine> sourceLines) {
        for (String rawLine : text.replace('\u00A0', ' ').split("\\R")) {
            String line = normalize(rawLine);
            if (looksLikeCandidate(line)) {
                sourceLines.add(new SourceLine(sourceType, line, null, html));
            }
        }
    }

    private ImportPreviewLine toPreviewLine(int sourcePosition, SourceLine sourceLine) {
        String label = normalize(sourceLine.label());
        String suppliedUrl = sourceLine.url() == null ? null : normalize(sourceLine.url());
        if (label.isEmpty() && (suppliedUrl == null || suppliedUrl.isEmpty())) {
            return null;
        }

        List<ImportWarning> warnings = new ArrayList<>();
        ParsedSongText parsedSongText = parseSongText(label, warnings);
        String normalizedUrl = null;
        if (suppliedUrl == null || suppliedUrl.isEmpty()) {
            warnings.add(new ImportWarning(
                    countUrls(label) > 1 ? "AMBIGUOUS_YOUTUBE_URL" : "MISSING_YOUTUBE_URL",
                    countUrls(label) > 1
                            ? "Mehrere Linkziele wurden gefunden; bitte den gew\u00fcnschten YouTube-Link ausw\u00e4hlen."
                            : "Es wurde kein YouTube-Link erkannt."
            ));
        } else {
            try {
                normalizedUrl = youtubeUrlNormalizer.normalize(suppliedUrl);
            } catch (ApiBadRequestException exception) {
                warnings.add(new ImportWarning("UNSUPPORTED_YOUTUBE_URL", "Der Link ist kein unterst\u00fctzter YouTube-Video-Link."));
                normalizedUrl = suppliedUrl;
            }
        }

        ImportPreviewStatus status = statusFor(parsedSongText, suppliedUrl, warnings);
        return new ImportPreviewLine(
                sourcePosition,
                sourceLine.sourceType(),
                label,
                parsedSongText.artist(),
                parsedSongText.title(),
                normalizedUrl,
                status,
                List.copyOf(warnings),
                false
        );
    }

    private static ParsedSongText parseSongText(String label, List<ImportWarning> warnings) {
        Matcher matcher = SEPARATOR.matcher(label);
        if (!matcher.find()) {
            warnings.add(new ImportWarning("MISSING_ARTIST_TITLE_SEPARATOR", "Interpret und Titel konnten nicht eindeutig getrennt werden."));
            return new ParsedSongText(null, null, false);
        }
        int separatorEnd = matcher.end();
        String artist = normalize(label.substring(0, matcher.start()));
        String title = normalize(label.substring(separatorEnd));
        boolean ambiguous = matcher.find();
        if (ambiguous) {
            warnings.add(new ImportWarning("AMBIGUOUS_ARTIST_TITLE_SEPARATOR", "Mehrere m\u00f6gliche Trennstriche wurden gefunden; die erste Trennung wurde verwendet."));
        }
        if (artist.isEmpty() || title.isEmpty()) {
            warnings.add(new ImportWarning("MISSING_ARTIST_OR_TITLE", "Interpret und Titel m\u00fcssen jeweils gef\u00fcllt sein."));
        }
        return new ParsedSongText(emptyToNull(artist), emptyToNull(title), ambiguous);
    }

    private static ImportPreviewStatus statusFor(ParsedSongText song, String suppliedUrl, List<ImportWarning> warnings) {
        if (song.artist() == null || song.title() == null || suppliedUrl == null || suppliedUrl.isEmpty()) {
            return ImportPreviewStatus.INCOMPLETE;
        }
        return warnings.isEmpty() ? ImportPreviewStatus.READY : ImportPreviewStatus.WARNING;
    }

    private static boolean looksLikeCandidate(String line) {
        return !line.isBlank() && (SEPARATOR.matcher(line).find() || URL.matcher(line).find());
    }

    private static int countUrls(String line) {
        Matcher matcher = URL.matcher(line);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static String identity(ImportPreviewLine line) {
        if (line.artist() == null || line.title() == null || line.youtubeUrl() == null) {
            return null;
        }
        return normalizeForIdentity(line.artist()) + "|" + normalizeForIdentity(line.title()) + "|" + normalizeForIdentity(line.youtubeUrl());
    }

    private static String normalizeForIdentity(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String emptyToNull(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String trimTrailingPunctuation(String value) {
        return value.replaceAll("[),.;]+$", "");
    }

    private record SourceLine(String sourceType, String label, String url, boolean html) {
    }

    private record ParsedSongText(String artist, String title, boolean ambiguous) {
    }
}
