package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Parses published historical source forms into preview rows. Clipboard extraction and each known source format
 * deliberately remain separate: an unknown line is retained for manual correction instead of guessed into a format.
 */
@Component
class HistoricalEntryImportParser {

    private static final String HTML_LINK_BOUNDARY = "CSC_X_TOOL_HTML_LINK_BOUNDARY";
    private static final ImportWarning UNRECOGNIZED_FORMAT = new ImportWarning(
            "UNRECOGNIZED_FORMAT", "Die Zeile entspricht keinem bekannten historischen Songlistenformat und muss manuell ergänzt werden."
    );
    private static final ImportWarning AMBIGUOUS_LINKS = new ImportWarning(
            "AMBIGUOUS_LINKS", "Mehrere Links gehören zu diesem Quellblock; kein Link wurde automatisch übernommen."
    );
    private static final ImportWarning REPRESENTATION_CONFLICT = new ImportWarning(
            "REPRESENTATION_CONFLICT", "HTML- und Plaintextdarstellung derselben Teilnehmerzuordnung widersprechen sich; bitte die Quelle manuell prüfen."
    );

    private final Map<String, String> countryCodesByName;
    private final List<HistoricalEntryImportFormatStrategy> formats = List.of(
            new LinkedParticipantPrefixHistoricalEntryFormatStrategy(),
            new ParentheticalHistoricalEntryFormatStrategy(),
            new AnnouncementHistoricalEntryFormatStrategy()
    );

    HistoricalEntryImportParser(CountryCatalog countries) {
        Map<String, String> names = new HashMap<>();
        for (Country country : countries.findAll()) names.put(HistoricalEntryImportText.normalized(country.name()), country.code());
        // Explicit historical source spellings are plausibility signals only, never fuzzy country matching.
        names.put(HistoricalEntryImportText.normalized("Vatikan"), "VA");
        names.put(HistoricalEntryImportText.normalized("Vatikanstaat"), "VA");
        names.put(HistoricalEntryImportText.normalized("Südkorea"), "KR");
        names.put(HistoricalEntryImportText.normalized("Türkei"), "TR");
        names.put(HistoricalEntryImportText.normalized("Südafrika"), "ZA");
        names.put(HistoricalEntryImportText.normalized("Jamaica"), "JM");
        this.countryCodesByName = Map.copyOf(names);
    }

    List<HistoricalImportPreviewLine> parse(String html, String text, List<HistoricalImportParticipant> participants) {
        List<HistoricalImportSourceLine> extracted = new ArrayList<>();
        appendHtml(html, extracted);
        appendText(text, extracted);

        List<ParsedSourceLine> parsed = extracted.stream().map(this::parseSource).toList();
        List<ParsedSourceLine> selected = deduplicateEquivalentRepresentations(markRepresentationConflicts(parsed));
        List<HistoricalImportPreviewLine> lines = new ArrayList<>();
        for (ParsedSourceLine source : selected) lines.add(toPreviewLine(lines.size() + 1, source, participants));
        return List.copyOf(lines);
    }

    private ParsedSourceLine parseSource(HistoricalImportSourceLine source) {
        Optional<HistoricalEntryImportParseResult> parsed = formats.stream().map(strategy -> strategy.parse(source))
                .flatMap(Optional::stream).findFirst();
        HistoricalEntryImportParseResult result = parsed.orElseGet(() -> new HistoricalEntryImportParseResult(
                null, null, null, null, null, List.of(UNRECOGNIZED_FORMAT)
        ));
        return new ParsedSourceLine(source, result.withAdditionalWarnings(source.extractionWarnings()));
    }

    private void appendHtml(String html, List<HistoricalImportSourceLine> target) {
        if (html == null || html.isBlank()) return;
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script,style,noscript,template").remove();
        List<Element> blocks = document.select("p,li,div").stream().filter(block -> block.children().stream().noneMatch(child ->
                child.normalName().equals("p") || child.normalName().equals("li") || child.normalName().equals("div")
        )).toList();
        if (blocks.isEmpty()) {
            if (document.body().select("a[href]").isEmpty()) appendText(document.body().wholeText(), target);
            else appendHtmlBlock(document.body(), target);
            return;
        }
        blocks.forEach(block -> appendHtmlBlock(block, target));
    }

    private void appendHtmlBlock(Element block, List<HistoricalImportSourceLine> target) {
        String visibleText = HistoricalEntryImportText.compact(block.text());
        if (!HistoricalEntryImportText.looksLikePotentialEntry(visibleText)) return;
        List<Element> links = block.select("a[href]");
        if (links.size() == 1) {
            Element link = links.getFirst();
            Element copy = block.clone();
            Element linkInCopy = copy.select("a[href]").getFirst();
            linkInCopy.before(HTML_LINK_BOUNDARY);
            linkInCopy.remove();
            String textAroundLink = copy.text();
            int boundary = textAroundLink.indexOf(HTML_LINK_BOUNDARY);
            target.add(new HistoricalImportSourceLine(
                    visibleText, HistoricalImportSourceLine.ClipboardRepresentation.RICH_HTML, link.attr("href"),
                    boundary < 0 ? "" : HistoricalEntryImportText.compact(textAroundLink.substring(0, boundary)),
                    HistoricalEntryImportText.compact(link.text()), List.of()
            ));
        } else if (links.size() > 1) {
            target.add(new HistoricalImportSourceLine(
                    visibleText, HistoricalImportSourceLine.ClipboardRepresentation.RICH_HTML, null, null, null, List.of(AMBIGUOUS_LINKS)
            ));
        } else {
            target.add(new HistoricalImportSourceLine(
                    visibleText, HistoricalImportSourceLine.ClipboardRepresentation.RICH_HTML, null, null, null, List.of()
            ));
        }
    }

    private static void appendText(String text, List<HistoricalImportSourceLine> target) {
        if (text == null || text.isBlank()) return;
        for (String raw : text.replace('\u00A0', ' ').split("\\R")) {
            String line = HistoricalEntryImportText.compact(raw);
            if (!HistoricalEntryImportText.looksLikePotentialEntry(line)) continue;
            if (HistoricalEntryImportText.hasMarkdownLink(line)) {
                target.add(new HistoricalImportSourceLine(
                        line, HistoricalImportSourceLine.ClipboardRepresentation.PLAIN_TEXT, null, null, null, List.of()
                ));
                continue;
            }
            var urls = HistoricalEntryImportText.HTTP_URL.matcher(line);
            List<String> found = new ArrayList<>();
            while (urls.find()) found.add(HistoricalEntryImportText.trimUrl(urls.group()));
            String label = found.size() == 1 ? HistoricalEntryImportText.compact(line.substring(0, line.indexOf(found.getFirst()))) : line;
            target.add(new HistoricalImportSourceLine(
                    label, HistoricalImportSourceLine.ClipboardRepresentation.PLAIN_TEXT, found.size() == 1 ? found.getFirst() : null,
                    null, null, List.of()
            ));
        }
    }

    private List<ParsedSourceLine> markRepresentationConflicts(List<ParsedSourceLine> parsed) {
        List<ParsedSourceLine> marked = new ArrayList<>(parsed);
        for (int left = 0; left < marked.size(); left++) {
            for (int right = left + 1; right < marked.size(); right++) {
                ParsedSourceLine first = marked.get(left);
                ParsedSourceLine second = marked.get(right);
                if (first.source().representation() == second.source().representation()
                        || equivalentIdentity(first).equals(equivalentIdentity(second))) continue;
                if (assignmentIdentity(first).isPresent() && assignmentIdentity(first).equals(assignmentIdentity(second))) {
                    marked.set(left, first.withWarning(REPRESENTATION_CONFLICT));
                    marked.set(right, second.withWarning(REPRESENTATION_CONFLICT));
                }
            }
        }
        return List.copyOf(marked);
    }

    private List<ParsedSourceLine> deduplicateEquivalentRepresentations(List<ParsedSourceLine> parsed) {
        Map<String, ParsedSourceLine> selected = new LinkedHashMap<>();
        for (ParsedSourceLine source : parsed) {
            String identity = equivalentIdentity(source);
            if (identity.isEmpty()) {
                selected.put("unresolved-" + selected.size(), source);
                continue;
            }
            ParsedSourceLine existing = selected.get(identity);
            if (existing == null || informationRank(source) > informationRank(existing)) selected.put(identity, source);
        }
        return List.copyOf(selected.values());
    }

    private static String equivalentIdentity(ParsedSourceLine source) {
        HistoricalEntryImportParseResult result = source.result();
        if (result.artist() == null || result.title() == null || result.firstAssignmentToken() == null || result.secondAssignmentToken() == null) return "";
        List<String> assignment = List.of(
                HistoricalEntryImportText.normalized(result.firstAssignmentToken()), HistoricalEntryImportText.normalized(result.secondAssignmentToken())
        ).stream().sorted(Comparator.naturalOrder()).toList();
        return String.join("\u001f", HistoricalEntryImportText.normalized(result.artist()), HistoricalEntryImportText.normalized(result.title()), assignment.get(0), assignment.get(1));
    }

    private static Optional<String> assignmentIdentity(ParsedSourceLine source) {
        HistoricalEntryImportParseResult result = source.result();
        if (result.firstAssignmentToken() == null || result.secondAssignmentToken() == null) return Optional.empty();
        return Optional.of(List.of(
                HistoricalEntryImportText.normalized(result.firstAssignmentToken()), HistoricalEntryImportText.normalized(result.secondAssignmentToken())
        ).stream().sorted().reduce((first, second) -> first + "\u001f" + second).orElseThrow());
    }

    private static int informationRank(ParsedSourceLine source) {
        if (!HistoricalEntryImportText.validHttpUrl(source.result().url())) return 0;
        return source.source().representation() == HistoricalImportSourceLine.ClipboardRepresentation.RICH_HTML ? 2 : 1;
    }

    private HistoricalImportPreviewLine toPreviewLine(
            int sourcePosition, ParsedSourceLine source, List<HistoricalImportParticipant> participants
    ) {
        HistoricalEntryImportParseResult parsed = source.result();
        List<ImportWarning> warnings = new ArrayList<>(parsed.warnings());
        String url = normalizeOptionalUrl(parsed.url(), warnings);
        Resolution resolution = resolve(parsed.firstAssignmentToken(), parsed.secondAssignmentToken(), participants, warnings);
        ImportPreviewStatus status = parsed.artist() == null || parsed.title() == null || resolution.participant() == null
                ? ImportPreviewStatus.INCOMPLETE : warnings.isEmpty() ? ImportPreviewStatus.READY : ImportPreviewStatus.WARNING;
        return new HistoricalImportPreviewLine(
                sourcePosition, source.source().sourceText(), parsed.artist(), parsed.title(), url,
                resolution.participantToken(), resolution.countryToken(),
                resolution.participant() == null ? null : resolution.participant().participantId(),
                resolution.participant() == null ? null : resolution.participant().displayName(),
                status, List.copyOf(warnings), null, false
        );
    }

    private Resolution resolve(
            String firstToken, String secondToken, List<HistoricalImportParticipant> participants, List<ImportWarning> warnings
    ) {
        if (firstToken == null || secondToken == null) return new Resolution(null, firstToken, secondToken);
        List<HistoricalImportParticipant> firstParticipants = matchingParticipants(firstToken, participants);
        List<HistoricalImportParticipant> secondParticipants = matchingParticipants(secondToken, participants);
        String firstCountry = countryCodesByName.get(HistoricalEntryImportText.normalized(firstToken));
        String secondCountry = countryCodesByName.get(HistoricalEntryImportText.normalized(secondToken));
        HistoricalImportParticipant participant = null;
        String statedCountry = null;
        String participantToken = firstToken;
        String countryToken = secondToken;
        if (firstParticipants.size() == 1 && secondCountry != null && secondParticipants.isEmpty()) {
            participant = firstParticipants.getFirst(); statedCountry = secondCountry;
        } else if (secondParticipants.size() == 1 && firstCountry != null && firstParticipants.isEmpty()) {
            participant = secondParticipants.getFirst(); statedCountry = firstCountry;
            participantToken = secondToken; countryToken = firstToken;
        } else if (firstParticipants.size() == 1 && secondParticipants.isEmpty()) {
            participant = firstParticipants.getFirst();
            warnings.add(new ImportWarning("UNKNOWN_COUNTRY", "Das angegebene Land ist nicht eindeutig im lokalen Katalog bekannt."));
        } else if (secondParticipants.size() == 1 && firstParticipants.isEmpty()) {
            participant = secondParticipants.getFirst();
            participantToken = secondToken; countryToken = firstToken;
            warnings.add(new ImportWarning("UNKNOWN_COUNTRY", "Das angegebene Land ist nicht eindeutig im lokalen Katalog bekannt."));
        } else if (firstParticipants.size() > 1 || secondParticipants.size() > 1 || (!firstParticipants.isEmpty() && !secondParticipants.isEmpty())) {
            warnings.add(new ImportWarning("AMBIGUOUS_PARTICIPANT", "Die Teilnehmerzuordnung ist mehrdeutig und muss manuell gewählt werden."));
        } else {
            if (firstCountry != null && secondCountry == null) {
                participantToken = secondToken; countryToken = firstToken;
            }
            warnings.add(new ImportWarning("UNRESOLVED_PARTICIPANT", "Der Teilnehmer ist in dieser CSC-Ausgabe nicht eindeutig gepflegt."));
        }
        if (participant != null && statedCountry != null && !statedCountry.equals(participant.countryCode())) {
            warnings.add(new ImportWarning("COUNTRY_CONFLICT", "Das Quellenland passt nicht zur gepflegten Contest-Teilnahme; die Teilnehmerzuordnung wurde nicht geändert."));
        }
        return new Resolution(participant, participantToken, countryToken);
    }

    private static List<HistoricalImportParticipant> matchingParticipants(String token, List<HistoricalImportParticipant> participants) {
        String needle = HistoricalEntryImportText.normalized(token);
        return participants.stream().filter(participant -> HistoricalEntryImportText.normalized(participant.displayName()).equals(needle)
                || participant.aliases().stream().anyMatch(alias -> HistoricalEntryImportText.normalized(alias).equals(needle))).toList();
    }

    private static String normalizeOptionalUrl(String url, List<ImportWarning> warnings) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(HistoricalEntryImportText.compact(url));
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException();
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            warnings.add(new ImportWarning("INVALID_SOURCE_URL", "Der Quelllink muss eine HTTP- oder HTTPS-Adresse sein; er kann entfernt oder korrigiert werden."));
            return HistoricalEntryImportText.compact(url);
        }
    }

    private record ParsedSourceLine(HistoricalImportSourceLine source, HistoricalEntryImportParseResult result) {
        ParsedSourceLine withWarning(ImportWarning warning) {
            return new ParsedSourceLine(source, result.withAdditionalWarnings(List.of(warning)));
        }
    }
    private record Resolution(HistoricalImportParticipant participant, String participantToken, String countryToken) { }
}
