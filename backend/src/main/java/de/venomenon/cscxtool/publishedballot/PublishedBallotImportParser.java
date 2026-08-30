package de.venomenon.cscxtool.publishedballot;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

/**
 * Converts only the representations from a user paste event into an ephemeral preview.  It uses a DOM parser
 * for HTML, never renders source markup and intentionally treats the points word as opaque formatting.
 */
@Component
class PublishedBallotImportParser {

    private static final Pattern HEADER = Pattern.compile("^\\s*\\[\\s*#?\\d+\\s*]\\s*(.*?)\\s*[-\\u2010-\\u2014]\\s*(.*?)\\s*$");
    private static final Pattern SONG_LINE = Pattern.compile("^(?:[\\s*_`]+)?\\d+\\s+\\S+\\s+(.+)$");

    private final Map<String, String> countriesByName;

    PublishedBallotImportParser(CountryCatalog countries) {
        Map<String, String> values = new HashMap<>();
        for (Country country : countries.findAll()) values.put(normalized(country.name()), country.code());
        // These spellings are source plausibility signals only; a name/alias still resolves the participant.
        values.put(normalized("Südkorea"), "KR");
        values.put(normalized("Südafrika"), "ZA");
        values.put(normalized("Türkei"), "TR");
        values.put(normalized("Vatikan"), "VA");
        this.countriesByName = Map.copyOf(values);
    }

    List<PublishedBallotPreviewBlock> parse(
            String html, String text, List<PublishedBallotParticipant> participants, List<PublishedBallotEntry> entries,
            java.util.Set<Long> existingParticipationIds
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
                if (element.children().stream().anyMatch(child -> List.of("p", "li", "div").contains(child.normalName()))) continue;
                String url = element.select("a[href]").stream().map(link -> compact(link.attr("href")))
                        .filter(value -> !value.isBlank()).findFirst().orElse(null);
                appendText(element.wholeText(), htmlLines, url);
            }
            if (htmlLines.isEmpty()) appendText(document.body().wholeText(), htmlLines, null);
        }
        // One paste event commonly carries both representations. Prefer the DOM-derived form so
        // identical song lines in consecutive ballots are not accidentally de-duplicated.
        if (!htmlLines.isEmpty()) return htmlLines;
        List<SourceLine> textLines = new ArrayList<>();
        appendText(text, textLines, null);
        return textLines;
    }

    private static void appendText(String source, List<SourceLine> result, String url) {
        if (source == null || source.isBlank()) return;
        for (String line : source.replace('\u00a0', ' ').split("\\R")) {
            String value = compact(line);
            if (!value.isBlank()) result.add(new SourceLine(value, url));
        }
    }

    private static List<Block> blocks(List<SourceLine> lines) {
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        for (SourceLine line : lines) {
            Matcher header = HEADER.matcher(line.text());
            if (header.matches()) {
                current = new Block(line.text(), compact(header.group(1)), compact(header.group(2)));
                blocks.add(current);
            } else if (current != null && SONG_LINE.matcher(line.text()).matches()) {
                current.songLines().add(line);
            }
        }
        if (blocks.isEmpty()) {
            Block unknown = new Block(null, null, null);
            for (SourceLine line : lines) if (SONG_LINE.matcher(line.text()).matches()) unknown.songLines().add(line);
            blocks.add(unknown);
        }
        return blocks;
    }

    private PublishedBallotPreviewBlock toPreview(
            int sourcePosition, Block block, List<PublishedBallotParticipant> participants, List<PublishedBallotEntry> entries,
            java.util.Set<Long> existingParticipationIds
    ) {
        List<BallotImportWarning> warnings = new ArrayList<>();
        ResolvedParticipant voter = resolveParticipant(block.firstHeaderToken(), block.secondHeaderToken(), participants, warnings);
        if (block.songLines().size() != 15) warnings.add(new BallotImportWarning("POSITION_COUNT", "Der Bewertungsblock muss genau 15 Songzeilen enthalten."));
        List<PublishedBallotPreviewPosition> positions = new ArrayList<>();
        for (int index = 0; index < block.songLines().size(); index++) {
            positions.add(resolvePosition(index + 1, 15 - index, block.songLines().get(index), entries, warnings));
        }
        boolean existing = voter.participant() != null && existingParticipationIds.contains(voter.participant().participationId());
        if (existing) warnings.add(new BallotImportWarning("EXISTING_BALLOT", "Für diesen Teilnehmer ist bereits ein Stimmzettelstatus gespeichert. Ein Ersatz muss bewusst bestätigt werden."));
        boolean complete = voter.participant() != null && positions.size() == 15
                && positions.stream().allMatch(position -> position.entryId() != null);
        String status = complete && warnings.stream().noneMatch(this::blocksImport) ? "READY" : complete ? "WARNING" : "INCOMPLETE";
        PublishedBallotParticipant participant = voter.participant();
        return new PublishedBallotPreviewBlock(sourcePosition,
                participant == null ? null : participant.participationId(), participant == null ? null : participant.participantId(),
                participant == null ? null : participant.displayName(), participant == null ? null : participant.countryCode(), existing,
                status, List.copyOf(positions), List.copyOf(warnings));
    }

    private boolean blocksImport(BallotImportWarning warning) {
        return switch (warning.code()) {
            case "POSITION_COUNT", "UNRESOLVED_VOTER", "AMBIGUOUS_VOTER" -> true;
            default -> false;
        };
    }

    private PublishedBallotPreviewPosition resolvePosition(
            int sourcePosition, int rank, SourceLine source, List<PublishedBallotEntry> entries,
            List<BallotImportWarning> blockWarnings
    ) {
        List<BallotImportWarning> warnings = new ArrayList<>();
        String songText = songText(source.text());
        List<PublishedBallotEntry> candidates = candidates(songText, source.url(), entries);
        PublishedBallotEntry entry = candidates.size() == 1 ? candidates.getFirst() : null;
        if (candidates.isEmpty()) warnings.add(new BallotImportWarning("UNRESOLVED_SONG", "Die Songzeile konnte keinem bestehenden Beitrag dieser Show zugeordnet werden."));
        if (candidates.size() > 1) warnings.add(new BallotImportWarning("AMBIGUOUS_SONG", "Mehrere vorhandene Beiträge passen auf diese Songzeile; bitte den Beitrag manuell wählen."));
        if (entry != null && participantHintConflicts(songText, entry, entries)) {
            warnings.add(new BallotImportWarning("SUBMITTER_CONFLICT", "Der Teilnehmer- oder Länderhinweis passt nicht zum Einreichenden des zugeordneten Beitrags."));
        }
        blockWarnings.addAll(warnings);
        return new PublishedBallotPreviewPosition(sourcePosition, rank, source.text(), entry == null ? null : entry.id(),
                entry == null ? null : entry.artist(), entry == null ? null : entry.title(),
                entry == null ? null : entry.submitterParticipantId(), entry == null ? null : entry.submitterDisplayName(), List.copyOf(warnings));
    }

    private static String songText(String source) {
        Matcher matcher = SONG_LINE.matcher(source);
        return matcher.matches() ? compact(matcher.group(1)) : compact(source);
    }

    private static List<PublishedBallotEntry> candidates(String text, String url, List<PublishedBallotEntry> entries) {
        String normalizedText = normalized(text);
        List<PublishedBallotEntry> matchedByUrl = url == null ? List.of() : entries.stream()
                .filter(entry -> entry.youtubeUrl() != null && normalized(entry.youtubeUrl()).equals(normalized(url))).toList();
        if (matchedByUrl.size() == 1) return matchedByUrl;
        return entries.stream().filter(entry -> normalizedText.contains(normalized(entry.artist() + " - " + entry.title()))).toList();
    }

    private boolean participantHintConflicts(String source, PublishedBallotEntry selected, List<PublishedBallotEntry> entries) {
        if (selected.submitterParticipantId() == null) return false;
        int songStart = source.toLowerCase(Locale.ROOT).indexOf(selected.artist().toLowerCase(Locale.ROOT));
        String beforeSong = songStart < 0 ? source : source.substring(0, songStart);
        String hint = normalized(beforeSong);
        if (hint.isBlank()) return false;
        for (PublishedBallotEntry entry : entries) {
            if (entry.submitterParticipantId() == null || entry.submitterParticipantId().equals(selected.submitterParticipantId())) continue;
            if ((entry.submitterDisplayName() != null && hint.contains(normalized(entry.submitterDisplayName())))
                    || (entry.submitterCountryCode() != null && countryNameHint(hint, entry.submitterCountryCode()))) return true;
        }
        return false;
    }

    private boolean countryNameHint(String source, String countryCode) {
        return countriesByName.entrySet().stream().anyMatch(country -> country.getValue().equals(countryCode) && source.contains(country.getKey()));
    }

    private ResolvedParticipant resolveParticipant(
            String first, String second, List<PublishedBallotParticipant> participants, List<BallotImportWarning> warnings
    ) {
        if (first == null || second == null) {
            warnings.add(new BallotImportWarning("UNRESOLVED_VOTER", "Eine Kopfzeile wie [#3] Land - Teilnehmer fehlt oder ist nicht lesbar."));
            return new ResolvedParticipant(null);
        }
        List<PublishedBallotParticipant> firstMatches = matching(first, participants);
        List<PublishedBallotParticipant> secondMatches = matching(second, participants);
        List<PublishedBallotParticipant> matches = firstMatches.size() == 1 && secondMatches.isEmpty() ? firstMatches
                : secondMatches.size() == 1 && firstMatches.isEmpty() ? secondMatches : List.of();
        if (matches.isEmpty()) {
            warnings.add(new BallotImportWarning(
                    firstMatches.size() > 1 || secondMatches.size() > 1 || (!firstMatches.isEmpty() && !secondMatches.isEmpty())
                            ? "AMBIGUOUS_VOTER" : "UNRESOLVED_VOTER",
                    "Der Abstimmende muss über Anzeigename oder Alias eindeutig im Teilnehmerfeld dieser CSC-Ausgabe aufgelöst werden."
            ));
            return new ResolvedParticipant(null);
        }
        PublishedBallotParticipant participant = matches.getFirst();
        String countryToken = firstMatches.size() == 1 ? second : first;
        String statedCountry = countriesByName.get(normalized(countryToken));
        if (statedCountry == null) warnings.add(new BallotImportWarning("UNKNOWN_COUNTRY", "Das Quellenland ist nicht im lokalen Katalog bekannt."));
        else if (!statedCountry.equals(participant.countryCode())) warnings.add(new BallotImportWarning("COUNTRY_CONFLICT", "Das Quellenland passt nicht zur gepflegten Contest-Teilnahme."));
        return new ResolvedParticipant(participant);
    }

    private static List<PublishedBallotParticipant> matching(String token, List<PublishedBallotParticipant> participants) {
        String needle = normalized(token);
        return participants.stream().filter(participant -> normalized(participant.displayName()).equals(needle)
                || participant.aliases().stream().anyMatch(alias -> normalized(alias).equals(needle))).toList();
    }
    private static String compact(String value) { return value == null ? "" : value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim(); }
    private static String normalized(String value) {
        return Normalizer.normalize(compact(value), Normalizer.Form.NFKC)
                .replace('\u2010', '-').replace('\u2011', '-').replace('\u2012', '-').replace('\u2013', '-').replace('\u2014', '-')
                .toLowerCase(Locale.ROOT);
    }
    private record SourceLine(String text, String url) { }
    private record Block(String header, String firstHeaderToken, String secondHeaderToken, List<SourceLine> songLines) {
        Block(String header, String firstHeaderToken, String secondHeaderToken) { this(header, firstHeaderToken, secondHeaderToken, new ArrayList<>()); }
    }
    private record ResolvedParticipant(PublishedBallotParticipant participant) { }
}
