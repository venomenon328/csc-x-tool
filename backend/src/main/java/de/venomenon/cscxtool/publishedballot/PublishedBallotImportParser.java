package de.venomenon.cscxtool.publishedballot;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

/**
 * Converts only the representations from a user paste event into an ephemeral preview. It uses a DOM parser
 * for HTML, never renders source markup and intentionally treats the points word as opaque formatting.
 */
@Component
class PublishedBallotImportParser {

    private static final Pattern STANDARD_HEADER = Pattern.compile("^\\s*\\[\\s*#?\\s*\\d+\\s*]\\s*(.*?)\\s*$");
    private static final Pattern ENCLOSED_HEADER = Pattern.compile("^\\s*\\[\\s*#?\\s*\\d+\\s+(.+?)\\s*]\\s*$");
    private static final Pattern EXPLICIT_RANKED_LINE = Pattern.compile(
            "^\\s*(\\d{1,2})\\.\\s+(.+?)\\s+(?:[*_`~]+\\s*)?(\\d+)\\s*([^\\s\\d]+)\\s*$"
    );
    private static final Pattern PARENTHETICAL_ASSIGNMENT = Pattern.compile(
            "\\(([^()]+?)\\s+[-–—]\\s+([^()]+?)\\)"
    );
    private static final char INLINE_BOUNDARY = '\u001f';

    private final Map<String, String> countriesByName;

    PublishedBallotImportParser(CountryCatalog countries) {
        Map<String, String> values = new HashMap<>();
        for (Country country : countries.findAll()) values.put(normalized(country.name()), country.code());
        // Historic source spellings used as plausibility signals only.
        values.put(normalized("Südkorea"), "KR");
        values.put(normalized("Südafrika"), "ZA");
        values.put(normalized("Türkei"), "TR");
        values.put(normalized("Vatikan"), "VA");
        this.countriesByName = Map.copyOf(values);
    }

    List<PublishedBallotPreviewBlock> parse(
            String html,
            String text,
            List<PublishedBallotParticipant> participants,
            List<PublishedBallotEntry> entries,
            Set<Long> existingParticipationIds
    ) {
        List<SourceLine> sources = extract(html, text);
        List<Block> blocks = blocks(sources);
        List<PublishedBallotPreviewBlock> previews = new ArrayList<>();
        for (int index = 0; index < blocks.size(); index++) {
            previews.add(toPreview(index + 1, blocks.get(index), participants, entries, existingParticipationIds));
        }
        return List.copyOf(previews);
    }

    private List<SourceLine> extract(String html, String text) {
        List<SourceLine> htmlLines = new ArrayList<>();
        if (html != null && !html.isBlank()) {
            Document document = Jsoup.parseBodyFragment(html);
            document.select("script,style,noscript,template").remove();
            document.select("br").forEach(element -> element.after(new TextNode("\n")));
            for (Element element : document.select("h1,h2,h3,h4,h5,h6,p,li,div")) {
                if (element.children().stream().anyMatch(child -> List.of("p", "li", "div").contains(child.normalName()))) {
                    continue;
                }
                appendHtmlElement(element, htmlLines);
            }
            if (htmlLines.isEmpty()) appendHtmlElement(document.body(), htmlLines);
        }
        // A paste commonly supplies HTML and plain text. Prefer the DOM form rather than importing both.
        if (!htmlLines.isEmpty()) return htmlLines;
        List<SourceLine> textLines = new ArrayList<>();
        appendText(text, textLines, null);
        return textLines;
    }

    private static void appendHtmlElement(Element element, List<SourceLine> result) {
        List<Element> links = element.select("a[href]");
        if (links.size() <= 1) {
            String url = links.stream().map(link -> compact(link.attr("href")))
                    .filter(value -> !value.isBlank()).findFirst().orElse(null);
            appendText(htmlText(element), result, url);
            return;
        }

        if (!element.select("br").isEmpty()) {
            String[] fragments = element.html().split("(?i)<br\\b[^>]*>");
            for (String fragment : fragments) {
                if (fragment.isBlank()) continue;
                Element logicalLine = Jsoup.parseBodyFragment(fragment).body();
                List<Element> lineLinks = logicalLine.select("a[href]");
                String url = lineLinks.size() == 1 ? compact(lineLinks.getFirst().attr("href")) : null;
                appendText(htmlText(logicalLine), result, url);
            }
            return;
        }

        // Never apply one arbitrary link to several logical rating lines.
        appendText(htmlText(element), result, null);
    }

    private static void appendText(String source, List<SourceLine> result, String url) {
        if (source == null || source.isBlank()) return;
        for (String line : source.replace('\u00a0', ' ').split("\\R")) {
            String value = compact(line.replace(INLINE_BOUNDARY, ' '));
            if (!value.isBlank()) result.add(new SourceLine(value, inlineStructure(line), url));
        }
    }

    private static String htmlText(Element element) {
        StringBuilder result = new StringBuilder();
        appendHtmlText(element, result);
        return result.toString();
    }

    private static boolean appendHtmlText(Node node, StringBuilder result) {
        if (node instanceof TextNode textNode) {
            String text = textNode.getWholeText();
            result.append(text);
            return !text.isBlank();
        }
        boolean hasText = false;
        for (Node child : node.childNodes()) {
            if (hasText) result.append(INLINE_BOUNDARY);
            hasText |= appendHtmlText(child, result);
        }
        return hasText;
    }

    private static List<Block> blocks(List<SourceLine> lines) {
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        for (SourceLine line : lines) {
            HeaderTokens header = parseHeader(line.text());
            if (header != null) {
                current = new Block(line.text(), header.firstToken(), header.secondToken());
                blocks.add(current);
            } else if (current != null && isSongLine(line)) {
                current.songLines().add(line);
            } else if (current != null && looksLikeRatingLine(line)) {
                current.unrecognizedRatingLines().add(line);
            }
        }
        if (blocks.isEmpty()) {
            Block unknown = new Block(null, null, null);
            for (SourceLine line : lines) {
                if (isSongLine(line)) unknown.songLines().add(line);
                else if (looksLikeRatingLine(line)) unknown.unrecognizedRatingLines().add(line);
            }
            blocks.add(unknown);
        }
        return blocks;
    }

    private static HeaderTokens parseHeader(String source) {
        String value = withoutMarkdownDecoration(source);
        Matcher standard = STANDARD_HEADER.matcher(value);
        Matcher enclosed = ENCLOSED_HEADER.matcher(value);
        String payload;
        if (standard.matches()) payload = compact(standard.group(1));
        else if (enclosed.matches()) payload = compact(enclosed.group(1));
        else return null;

        List<Integer> preferredSeparators = new ArrayList<>();
        List<Integer> allSeparators = new ArrayList<>();
        for (int index = 0; index < payload.length(); index++) {
            if (!isHeaderSeparator(payload.charAt(index))) continue;
            allSeparators.add(index);
            boolean spacedLeft = index > 0 && isHeaderSpace(payload.charAt(index - 1));
            boolean spacedRight = index + 1 < payload.length() && isHeaderSpace(payload.charAt(index + 1));
            if (spacedLeft && spacedRight) preferredSeparators.add(index);
        }
        int separator;
        if (preferredSeparators.size() == 1) separator = preferredSeparators.getFirst();
        else if (preferredSeparators.size() > 1 || allSeparators.size() != 1) return null;
        else separator = allSeparators.getFirst();

        String first = compact(payload.substring(0, separator));
        String second = compact(payload.substring(separator + 1));
        return first.isBlank() || second.isBlank() ? null : new HeaderTokens(first, second);
    }

    private static boolean isHeaderSeparator(char value) {
        return value == '-' || value >= '\u2010' && value <= '\u2014';
    }

    private static boolean isHeaderSpace(char value) {
        return Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private PublishedBallotPreviewBlock toPreview(
            int sourcePosition,
            Block block,
            List<PublishedBallotParticipant> participants,
            List<PublishedBallotEntry> entries,
            Set<Long> existingParticipationIds
    ) {
        List<BallotImportWarning> warnings = new ArrayList<>();
        ResolvedParticipant voter = resolveParticipant(
                block.firstHeaderToken(), block.secondHeaderToken(), participants, warnings
        );
        if (block.songLines().size() != 15) {
            warnings.add(new BallotImportWarning("POSITION_COUNT", "Der Bewertungsblock muss genau 15 Songzeilen enthalten."));
        }
        if (!block.unrecognizedRatingLines().isEmpty()) {
            warnings.add(new BallotImportWarning(
                    "UNRECOGNIZED_POSITION_LINES",
                    "Bewertungszeilen konnten nicht erkannt werden; bitte Punktepräfix und Formatierung prüfen."
            ));
        }
        boolean explicitRankMismatch = false;
        for (int index = 0; index < block.songLines().size(); index++) {
            ExplicitRankedLine explicit = explicitRankedLine(block.songLines().get(index).text());
            if (explicit != null && explicit.rank() != 15 - index) explicitRankMismatch = true;
        }
        if (explicitRankMismatch) {
            warnings.add(new BallotImportWarning(
                    "EXPLICIT_RANK_SEQUENCE",
                    "Explizite Rangnummern müssen vollständig in der Reihenfolge 15 bis 1 vorliegen."
            ));
        }
        List<PublishedBallotPreviewPosition> positions = new ArrayList<>();
        for (int index = 0; index < block.songLines().size(); index++) {
            positions.add(resolvePosition(
                    index + 1, 15 - index, block.songLines().get(index), entries, participants, warnings
            ));
        }
        boolean existing = voter.participant() != null
                && existingParticipationIds.contains(voter.participant().participationId());
        if (existing) {
            warnings.add(new BallotImportWarning(
                    "EXISTING_BALLOT",
                    "Für diesen Teilnehmer ist bereits ein Stimmzettelstatus gespeichert. Ein Ersatz muss bewusst bestätigt werden."
            ));
        }
        boolean complete = voter.participant() != null && positions.size() == 15
                && positions.stream().allMatch(position -> position.entryId() != null);
        String status = complete && warnings.stream().noneMatch(this::blocksImport)
                ? "READY" : complete ? "WARNING" : "INCOMPLETE";
        PublishedBallotParticipant participant = voter.participant();
        return new PublishedBallotPreviewBlock(
                sourcePosition,
                participant == null ? null : participant.participationId(),
                participant == null ? null : participant.participantId(),
                participant == null ? null : participant.displayName(),
                participant == null ? null : participant.countryCode(),
                existing,
                status,
                List.copyOf(positions),
                List.copyOf(warnings)
        );
    }

    private boolean blocksImport(BallotImportWarning warning) {
        return switch (warning.code()) {
            case "POSITION_COUNT", "UNRESOLVED_VOTER", "AMBIGUOUS_VOTER", "COUNTRY_CONFLICT",
                    "UNRECOGNIZED_POSITION_LINES", "UNRESOLVED_SONG", "AMBIGUOUS_SONG", "SOURCE_CONFLICT",
                    "SUBMITTER_CONFLICT", "EXPLICIT_RANK_SEQUENCE" -> true;
            default -> false;
        };
    }

    private PublishedBallotPreviewPosition resolvePosition(
            int sourcePosition,
            int rank,
            SourceLine source,
            List<PublishedBallotEntry> entries,
            List<PublishedBallotParticipant> participants,
            List<BallotImportWarning> blockWarnings
    ) {
        List<BallotImportWarning> warnings = new ArrayList<>();
        String songText = songText(source);
        EntryResolution resolution = resolveEntry(songText, source.url(), entries);
        warnings.addAll(resolution.warnings());
        PublishedBallotEntry entry = resolution.entry();
        if (entry != null) {
            List<BallotImportWarning> submitterWarnings = submitterWarnings(songText, entry, participants);
            warnings.addAll(submitterWarnings);
            if (submitterWarnings.stream().anyMatch(warning -> "SUBMITTER_CONFLICT".equals(warning.code()))) {
                entry = null;
            }
        }
        blockWarnings.addAll(warnings);
        return new PublishedBallotPreviewPosition(
                sourcePosition, rank, source.text(), entry == null ? null : entry.id(),
                entry == null ? null : entry.artist(), entry == null ? null : entry.title(),
                entry == null ? null : entry.submitterParticipantId(),
                entry == null ? null : entry.submitterDisplayName(),
                List.copyOf(warnings)
        );
    }

    private EntryResolution resolveEntry(String text, String url, List<PublishedBallotEntry> entries) {
        String normalizedText = normalized(text);
        List<PublishedBallotEntry> textMatches = entries.stream()
                .filter(entry -> normalizedText.contains(normalized(entry.artist() + " - " + entry.title())))
                .toList();
        if (url == null || url.isBlank()) return resolveWithoutUrl(textMatches);

        List<PublishedBallotEntry> urlMatches = entries.stream()
                .filter(entry -> entry.youtubeUrl() != null && normalized(entry.youtubeUrl()).equals(normalized(url)))
                .toList();
        if (urlMatches.size() == 1) {
            PublishedBallotEntry urlEntry = urlMatches.getFirst();
            if (textMatches.size() == 1 && textMatches.getFirst().id() != urlEntry.id()) {
                return conflict("SOURCE_CONFLICT", "Link und sichtbarer Interpret/Titel verweisen auf unterschiedliche vorhandene Beiträge.");
            }
            if (textMatches.size() > 1 && textMatches.stream().noneMatch(entry -> entry.id() == urlEntry.id())) {
                return conflict("SOURCE_CONFLICT", "Link und sichtbarer Songtext lassen sich nicht auf denselben vorhandenen Beitrag zurückführen.");
            }
            if (textMatches.isEmpty()) {
                return new EntryResolution(urlEntry, List.of(new BallotImportWarning(
                        "TEXT_MISMATCH",
                        "Der Link identifiziert einen vorhandenen Beitrag, Interpret/Titel stimmen aber nicht exakt mit dem gespeicherten Text überein."
                )));
            }
            return new EntryResolution(urlEntry, List.of());
        }
        if (urlMatches.isEmpty()) {
            if (textMatches.size() == 1) {
                return new EntryResolution(textMatches.getFirst(), List.of(new BallotImportWarning(
                        "UNKNOWN_SOURCE_URL",
                        "Der Quelllink ist lokal unbekannt; der Beitrag wurde ausschließlich über den eindeutigen Songtext erkannt."
                )));
            }
            return resolveWithoutUrl(textMatches);
        }
        if (textMatches.size() == 1 && urlMatches.stream().anyMatch(entry -> entry.id() == textMatches.getFirst().id())) {
            return new EntryResolution(textMatches.getFirst(), List.of(new BallotImportWarning(
                    "AMBIGUOUS_SOURCE_URL",
                    "Der Link kommt bei mehreren vorhandenen Beiträgen vor; der sichtbare Songtext löst die Zuordnung eindeutig auf."
            )));
        }
        return conflict("AMBIGUOUS_SONG", "Mehrere vorhandene Beiträge passen auf Link oder Songtext; bitte den Beitrag manuell wählen.");
    }

    private static EntryResolution resolveWithoutUrl(List<PublishedBallotEntry> textMatches) {
        if (textMatches.size() == 1) return new EntryResolution(textMatches.getFirst(), List.of());
        if (textMatches.isEmpty()) {
            return conflict("UNRESOLVED_SONG", "Die Songzeile konnte keinem bestehenden Beitrag dieser Show zugeordnet werden.");
        }
        return conflict("AMBIGUOUS_SONG", "Mehrere vorhandene Beiträge passen auf diese Songzeile; bitte den Beitrag manuell wählen.");
    }

    private static EntryResolution conflict(String code, String message) {
        return new EntryResolution(null, List.of(new BallotImportWarning(code, message)));
    }

    private List<BallotImportWarning> submitterWarnings(
            String source,
            PublishedBallotEntry selected,
            List<PublishedBallotParticipant> participants
    ) {
        if (selected.submitterParticipationId() == null) {
            return List.of(new BallotImportWarning(
                    "SUBMITTER_CONFLICT", "Der zugeordnete Beitrag besitzt keinen gültigen Einreichenden."
            ));
        }
        PublishedBallotParticipant submitter = participants.stream()
                .filter(participant -> participant.participationId() == selected.submitterParticipationId())
                .findFirst().orElse(null);
        if (submitter == null) {
            return List.of(new BallotImportWarning(
                    "SUBMITTER_CONFLICT", "Der Einreichende des Beitrags gehört nicht zum Teilnehmerfeld dieser CSC-Ausgabe."
            ));
        }
        int songStart = source.toLowerCase(Locale.ROOT).indexOf(selected.artist().toLowerCase(Locale.ROOT));
        if (songStart < 0) return List.of();
        StringBuilder hintSource = new StringBuilder(source.substring(0, songStart));
        Matcher parenthetical = PARENTHETICAL_ASSIGNMENT.matcher(source.substring(songStart));
        while (parenthetical.find()) {
            hintSource.append(' ').append(parenthetical.group(1)).append(' ').append(parenthetical.group(2));
        }
        String hint = normalized(hintSource.toString());
        boolean participantMatches = hint.contains(normalized(submitter.displayName()))
                || submitter.aliases().stream().anyMatch(alias -> hint.contains(normalized(alias)));
        Set<String> hintedCountries = new HashSet<>();
        countriesByName.forEach((name, code) -> {
            if (hint.contains(name)) hintedCountries.add(code);
        });
        boolean countryConflicts = !hintedCountries.isEmpty() && !hintedCountries.contains(submitter.countryCode());
        if (!participantMatches || countryConflicts) {
            return List.of(new BallotImportWarning(
                    "SUBMITTER_CONFLICT",
                    "Der Teilnehmer- oder Länderhinweis passt nicht zum Einreichenden des zugeordneten Beitrags; bitte manuell zuordnen."
            ));
        }
        if (hintedCountries.isEmpty()) {
            return List.of(new BallotImportWarning(
                    "UNKNOWN_SUBMITTER_COUNTRY",
                    "Das in der Songzeile genannte Land konnte nicht eindeutig geprüft werden."
            ));
        }
        return List.of();
    }

    private ResolvedParticipant resolveParticipant(
            String first,
            String second,
            List<PublishedBallotParticipant> participants,
            List<BallotImportWarning> warnings
    ) {
        if (first == null || second == null) {
            warnings.add(new BallotImportWarning(
                    "UNRESOLVED_VOTER", "Eine Kopfzeile wie [#3] Land - Teilnehmer fehlt oder ist nicht lesbar."
            ));
            return new ResolvedParticipant(null);
        }
        List<PublishedBallotParticipant> firstMatches = matching(first, participants);
        List<PublishedBallotParticipant> secondMatches = matching(second, participants);
        List<PublishedBallotParticipant> matches = firstMatches.size() == 1 && secondMatches.isEmpty()
                ? firstMatches : secondMatches.size() == 1 && firstMatches.isEmpty() ? secondMatches : List.of();
        if (matches.isEmpty()) {
            warnings.add(new BallotImportWarning(
                    firstMatches.size() > 1 || secondMatches.size() > 1
                            || (!firstMatches.isEmpty() && !secondMatches.isEmpty())
                            ? "AMBIGUOUS_VOTER" : "UNRESOLVED_VOTER",
                    "Der Abstimmende muss über Anzeigename oder Alias eindeutig im Teilnehmerfeld dieser CSC-Ausgabe aufgelöst werden."
            ));
            return new ResolvedParticipant(null);
        }
        PublishedBallotParticipant participant = matches.getFirst();
        String countryToken = firstMatches.size() == 1 ? second : first;
        String statedCountry = countriesByName.get(normalized(countryToken));
        if (statedCountry == null) {
            warnings.add(new BallotImportWarning("UNKNOWN_COUNTRY", "Das Quellenland ist nicht im lokalen Katalog bekannt."));
        } else if (!statedCountry.equals(participant.countryCode())) {
            warnings.add(new BallotImportWarning(
                    "COUNTRY_CONFLICT",
                    "Das Quellenland passt nicht zur gepflegten Contest-Teilnahme; bitte den Abstimmenden bewusst manuell wählen."
            ));
            return new ResolvedParticipant(null);
        }
        return new ResolvedParticipant(participant);
    }

    private static List<PublishedBallotParticipant> matching(
            String token, List<PublishedBallotParticipant> participants
    ) {
        String needle = normalized(token);
        return participants.stream().filter(participant -> normalized(participant.displayName()).equals(needle)
                || participant.aliases().stream().anyMatch(alias -> normalized(alias).equals(needle))).toList();
    }

    private static boolean isSongLine(SourceLine source) {
        return explicitRankedLine(source.text()) != null || !songTexts(source.structure()).isEmpty();
    }

    private static boolean looksLikeRatingLine(SourceLine source) {
        String structure = source.structure();
        return explicitRankedLine(source.text()) != null
                || !songTexts(structure).isEmpty()
                || afterLeadingDecimal(structure) >= 0
                || afterLeadingDecorationThenDecimal(structure) >= 0;
    }

    private static String songText(SourceLine source) {
        ExplicitRankedLine explicit = explicitRankedLine(source.text());
        if (explicit != null) return explicit.songText();
        List<String> candidates = songTexts(source.structure());
        return candidates.isEmpty() ? compact(source.text()) : candidates.getFirst();
    }

    private static ExplicitRankedLine explicitRankedLine(String source) {
        Matcher matcher = EXPLICIT_RANKED_LINE.matcher(compact(source));
        if (!matcher.matches()) return null;
        String pointsWord = matcher.group(4);
        if (pointsWord.codePoints().noneMatch(Character::isLetter)) return null;
        String songText = compact(matcher.group(2));
        if (songText.isBlank()) return null;
        return new ExplicitRankedLine(Integer.parseInt(matcher.group(1)), songText);
    }

    /**
     * Separates a visible score prefix without assigning meaning to its word, language, script or displayed value.
     * The decimal marker may appear before or after one opaque decoration token. Markdown and HTML boundaries are
     * candidates for the separator to the actual ballot content; the longest valid prefix wins.
     */
    private static List<String> songTexts(String source) {
        List<String> candidates = new ArrayList<>();
        for (int index = 0; index < source.length(); index++) {
            if (!isSeparator(source.charAt(index))) continue;
            String prefix = source.substring(0, index);
            if (!isStructuredScorePrefix(prefix)) continue;
            int contentStart = index;
            while (contentStart < source.length() && isSeparator(source.charAt(contentStart))) contentStart++;
            String content = compact(withoutInlineBoundaries(source.substring(contentStart)));
            if (!content.isBlank() && !candidates.contains(content)) candidates.add(0, content);
        }
        return List.copyOf(candidates);
    }

    private static boolean isStructuredScorePrefix(String source) {
        String prefix = compact(withoutInlineBoundaries(source));
        if (prefix.isBlank()) return false;

        int numberStart = -1;
        int numberEnd = -1;
        boolean numberFinished = false;
        for (int index = 0; index < prefix.length(); index++) {
            if (Character.isDigit(prefix.charAt(index))) {
                if (numberFinished) return false;
                if (numberStart < 0) numberStart = index;
                numberEnd = index + 1;
            } else if (numberStart >= 0) {
                numberFinished = true;
            }
        }
        if (numberStart < 0 || numberStart != 0 && numberEnd != prefix.length()) return false;

        String decoration = compact(prefix.substring(0, numberStart) + prefix.substring(numberEnd));
        if (decoration.isBlank()) return false;
        for (int index = 0; index < decoration.length(); index++) {
            char value = decoration.charAt(index);
            if (Character.isWhitespace(value) || Character.isSpaceChar(value)) return false;
        }
        return decoration.codePoints().anyMatch(Character::isLetter);
    }

    private static int afterLeadingDecimal(String source) {
        int index = 0;
        while (index < source.length() && isSeparator(source.charAt(index))) index++;
        int numberStart = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        return index == numberStart ? -1 : index;
    }

    private static int afterLeadingDecorationThenDecimal(String source) {
        int index = 0;
        while (index < source.length() && isSeparator(source.charAt(index))) index++;
        int decorationStart = index;
        boolean hasLetter = false;
        while (index < source.length() && !isSeparator(source.charAt(index))) {
            if (Character.isDigit(source.charAt(index))) return -1;
            if (Character.isLetter(source.charAt(index))) hasLetter = true;
            index++;
        }
        if (index == decorationStart || !hasLetter) return -1;
        while (index < source.length() && isSeparator(source.charAt(index))) index++;
        int numberStart = index;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        return index == numberStart ? -1 : index;
    }

    private static boolean isSeparator(char value) {
        return value == INLINE_BOUNDARY || Character.isWhitespace(value) || Character.isSpaceChar(value);
    }

    private static String inlineStructure(String source) {
        String boundary = String.valueOf(INLINE_BOUNDARY);
        return source.replaceAll("[*_]{2,}|`+", boundary)
                .replaceAll("(?<![\\p{L}\\p{N}])[*_](?=\\S)|(?<=\\S)[*_](?![\\p{L}\\p{N}])", boundary);
    }

    private static String withoutInlineBoundaries(String value) {
        return value.replace(String.valueOf(INLINE_BOUNDARY), "");
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String withoutMarkdownDecoration(String value) {
        return compact(value).replaceAll("(?<!\\w)[*_`]+|[*_`]+(?!\\w)", "");
    }

    private static String normalized(String value) {
        return Normalizer.normalize(compact(value), Normalizer.Form.NFKC)
                .replace('\u2010', '-').replace('\u2011', '-').replace('\u2012', '-')
                .replace('\u2013', '-').replace('\u2014', '-').toLowerCase(Locale.ROOT);
    }

    private record SourceLine(String text, String structure, String url) { }
    private record HeaderTokens(String firstToken, String secondToken) { }
    private record ExplicitRankedLine(int rank, String songText) { }
    private record Block(
            String header,
            String firstHeaderToken,
            String secondHeaderToken,
            List<SourceLine> songLines,
            List<SourceLine> unrecognizedRatingLines
    ) {
        Block(String header, String firstHeaderToken, String secondHeaderToken) {
            this(header, firstHeaderToken, secondHeaderToken, new ArrayList<>(), new ArrayList<>());
        }
    }
    private record ResolvedParticipant(PublishedBallotParticipant participant) { }
    private record EntryResolution(PublishedBallotEntry entry, List<BallotImportWarning> warnings) { }
}
