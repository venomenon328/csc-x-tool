package de.venomenon.cscxtool.publishedballot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/** Normalizes the current CyBoard voting-table paste shape into the parser's established logical line format. */
final class CyBoardTableBallotPasteNormalizer {

    private static final Pattern BALLOT_HEADING = Pattern.compile(
            "^Wertung\\s*#\\s*(\\d+)\\s*$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE
    );
    private static final Pattern ASSIGNMENT = Pattern.compile("^(.+?)\\s+[-–—]\\s+(.+?)$");
    private static final Pattern SCORE_LABEL = Pattern.compile("^(\\d{1,3})\\s+(\\S+)$");

    private CyBoardTableBallotPasteNormalizer() { }

    static String normalize(String html, String text) {
        String normalizedHtml = normalizeLines(htmlLines(html));
        if (normalizedHtml != null) return normalizedHtml;
        return normalizeLines(textLines(text));
    }

    private static String normalizeLines(List<PasteLine> lines) {
        List<String> normalized = new ArrayList<>();
        Integer ballotNumber = null;
        boolean voterSeen = false;
        String pendingScore = null;
        boolean sawHeading = false;
        int ratingCount = 0;

        for (PasteLine line : lines) {
            Matcher heading = BALLOT_HEADING.matcher(stripDecoration(line.text()));
            if (heading.matches()) {
                ballotNumber = Integer.parseInt(heading.group(1));
                voterSeen = false;
                pendingScore = null;
                sawHeading = true;
                continue;
            }
            if (ballotNumber == null) continue;

            if (!voterSeen) {
                String assignment = singleAssignmentCell(line);
                if (assignment != null) {
                    normalized.add("[#" + ballotNumber + "] " + assignment);
                    voterSeen = true;
                }
                continue;
            }

            String score = scoreLabel(line);
            if (score != null) {
                pendingScore = score;
                continue;
            }
            if (pendingScore == null) continue;

            String rating = ratingRow(line);
            if (rating != null) {
                normalized.add(pendingScore + " " + rating);
                pendingScore = null;
                ratingCount++;
            }
        }

        if (!sawHeading || normalized.isEmpty() || ratingCount == 0) return null;
        return String.join("\n", normalized);
    }

    private static String singleAssignmentCell(PasteLine line) {
        List<String> cells = meaningfulCells(line.cells());
        if (cells.size() != 1) return null;
        String value = cells.getFirst();
        return ASSIGNMENT.matcher(value).matches() ? value : null;
    }

    private static String scoreLabel(PasteLine line) {
        if (!line.cells().isEmpty()) return null;
        String value = stripDecoration(line.text());
        Matcher matcher = SCORE_LABEL.matcher(value);
        if (!matcher.matches()) return null;
        String pointsWord = matcher.group(2);
        if (pointsWord.codePoints().noneMatch(Character::isLetter)) return null;
        return matcher.group(1) + " " + pointsWord;
    }

    private static String ratingRow(PasteLine line) {
        List<String> cells = meaningfulCells(line.cells());
        if (cells.size() != 2) return null;
        String song = cells.get(0);
        String assignment = cells.get(1);
        if (!ASSIGNMENT.matcher(song).matches() || !ASSIGNMENT.matcher(assignment).matches()) return null;
        return song + " " + assignment;
    }

    private static List<String> meaningfulCells(List<String> cells) {
        return cells.stream()
                .map(CyBoardTableBallotPasteNormalizer::stripDecoration)
                .filter(value -> !value.isBlank() && !isAlignmentCell(value))
                .toList();
    }

    private static boolean isAlignmentCell(String value) {
        return value.matches("^:?-+:?$");
    }

    private static List<PasteLine> textLines(String source) {
        if (source == null || source.isBlank()) return List.of();
        List<PasteLine> lines = new ArrayList<>();
        for (String rawLine : source.replace('\u00a0', ' ').split("\\R")) {
            String text = compact(unescapeMarkdown(rawLine));
            if (text.isBlank()) continue;
            List<String> cells = markdownCells(rawLine);
            lines.add(new PasteLine(cells.isEmpty() ? text : String.join(" ", cells), cells));
        }
        return List.copyOf(lines);
    }

    private static List<String> markdownCells(String source) {
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.length() < 2 || !trimmed.startsWith("|") || !trimmed.endsWith("|")) return List.of();
        String body = trimmed.substring(1, trimmed.length() - 1);
        String[] rawCells = body.split("\\|", -1);
        List<String> cells = new ArrayList<>();
        for (String rawCell : rawCells) cells.add(compact(unescapeMarkdown(rawCell)));
        return List.copyOf(cells);
    }

    private static List<PasteLine> htmlLines(String html) {
        if (html == null || html.isBlank()) return List.of();
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script,style,noscript,template").remove();
        List<PasteLine> lines = new ArrayList<>();
        collectHtml(document.body(), lines);
        return List.copyOf(lines);
    }

    private static void collectHtml(Element element, List<PasteLine> lines) {
        String tag = element.normalName().toLowerCase(Locale.ROOT);
        if ("tr".equals(tag)) {
            List<String> cells = element.children().stream()
                    .filter(child -> "td".equals(child.normalName()) || "th".equals(child.normalName()))
                    .map(Element::text)
                    .map(CyBoardTableBallotPasteNormalizer::compact)
                    .toList();
            if (!cells.isEmpty()) lines.add(new PasteLine(String.join(" ", cells), cells));
            return;
        }
        if (List.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "li").contains(tag)) {
            String text = compact(element.text());
            if (!text.isBlank()) lines.add(new PasteLine(text, List.of()));
            return;
        }

        String ownText = compact(element.ownText());
        if (!ownText.isBlank()) lines.add(new PasteLine(ownText, List.of()));
        for (Element child : element.children()) collectHtml(child, lines);
    }

    private static String stripDecoration(String source) {
        String value = compact(unescapeMarkdown(source));
        int start = 0;
        int end = value.length();
        while (start < end && isDecoration(value.charAt(start))) start++;
        while (end > start && isDecoration(value.charAt(end - 1))) end--;
        return compact(value.substring(start, end));
    }

    private static boolean isDecoration(char value) {
        return value == '*' || value == '_' || value == '`' || value == '~';
    }

    private static String unescapeMarkdown(String source) {
        if (source == null || source.indexOf('\\') < 0) return source == null ? "" : source;
        StringBuilder result = new StringBuilder(source.length());
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '\\' && index + 1 < source.length() && isMarkdownEscapable(source.charAt(index + 1))) {
                result.append(source.charAt(++index));
            } else {
                result.append(value);
            }
        }
        return result.toString();
    }

    private static boolean isMarkdownEscapable(char value) {
        return "\\`*_{}[]()#+-.!|>".indexOf(value) >= 0;
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private record PasteLine(String text, List<String> cells) { }
}
