package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.net.URI;
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
import org.springframework.stereotype.Component;

/**
 * Parses the published historical source form into preview rows.  It has no persistence dependency and never
 * logs source text; every unresolved or conflicting line remains explicit for the user to correct.
 */
@Component
class HistoricalEntryImportParser {

    private static final Pattern ASSIGNMENT = Pattern.compile("^(.*?)\\s*\\(([^()/]+)\\s*/\\s*([^()/]+)\\)\\s*$");
    private static final Pattern SONG_SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");
    private static final Pattern URL = Pattern.compile("https?://[^\\s<>()]+", Pattern.CASE_INSENSITIVE);

    private final Map<String, String> countryCodesByName;

    HistoricalEntryImportParser(CountryCatalog countries) {
        Map<String, String> names = new HashMap<>();
        for (Country country : countries.findAll()) names.put(normalized(country.name()), country.code());
        // Common historic forum spellings are only used as plausibility signals, never as participant resolution.
        names.put(normalized("Vatikan"), "VA");
        names.put(normalized("Südkorea"), "KR");
        names.put(normalized("Türkei"), "TR");
        names.put(normalized("Südafrika"), "ZA");
        names.put(normalized("Jamaica"), "JM");
        this.countryCodesByName = Map.copyOf(names);
    }

    List<HistoricalImportPreviewLine> parse(String html, String text, List<HistoricalImportParticipant> participants) {
        List<SourceLine> sourceLines = new ArrayList<>();
        appendHtml(html, sourceLines);
        appendText(text, sourceLines);
        List<HistoricalImportPreviewLine> lines = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SourceLine source : sourceLines) {
            String identity = normalized(source.text()) + "|" + normalized(source.url());
            if (!seen.add(identity)) continue;
            lines.add(toPreviewLine(lines.size() + 1, source, participants));
        }
        return List.copyOf(lines);
    }

    private void appendHtml(String html, List<SourceLine> target) {
        if (html == null || html.isBlank()) return;
        Document document = Jsoup.parseBodyFragment(html);
        document.select("script,style,noscript,template").remove();
        for (Element block : document.select("p,li,div")) {
            if (block.children().stream().anyMatch(child -> child.normalName().equals("p")
                    || child.normalName().equals("li") || child.normalName().equals("div"))) continue;
            String value = compact(block.text());
            if (looksLikeEntry(value)) {
                Element link = block.select("a[href]").first();
                target.add(new SourceLine(value, link == null ? null : compact(link.attr("href"))));
            }
        }
        if (target.isEmpty()) appendText(document.body().wholeText(), target);
    }

    private static void appendText(String text, List<SourceLine> target) {
        if (text == null || text.isBlank()) return;
        for (String raw : text.replace('\u00A0', ' ').split("\\R")) {
            String line = compact(raw);
            if (!looksLikeEntry(line)) continue;
            Matcher urls = URL.matcher(line);
            List<String> found = new ArrayList<>();
            while (urls.find()) found.add(trimUrl(urls.group()));
            String label = found.size() == 1 ? compact(line.substring(0, line.indexOf(found.getFirst()))) : line;
            target.add(new SourceLine(label, found.size() == 1 ? found.getFirst() : null));
        }
    }

    private HistoricalImportPreviewLine toPreviewLine(
            int sourcePosition, SourceLine source, List<HistoricalImportParticipant> participants
    ) {
        List<ImportWarning> warnings = new ArrayList<>();
        String sourceText = compact(source.text());
        Matcher assignment = ASSIGNMENT.matcher(sourceText);
        String songText = sourceText;
        String firstToken = null;
        String secondToken = null;
        if (assignment.matches()) {
            songText = compact(assignment.group(1));
            firstToken = compact(assignment.group(2));
            secondToken = compact(assignment.group(3));
        } else {
            warnings.add(new ImportWarning("MISSING_ASSIGNMENT", "Teilnehmer und Land im abschließenden Klammerpaar fehlen oder sind unvollständig."));
        }

        String artist = null;
        String title = null;
        Matcher separator = SONG_SEPARATOR.matcher(songText);
        if (separator.find()) {
            artist = emptyToNull(compact(songText.substring(0, separator.start())));
            title = emptyToNull(compact(songText.substring(separator.end())));
        }
        if (artist == null || title == null) {
            warnings.add(new ImportWarning("MISSING_ARTIST_OR_TITLE", "Interpret und Titel konnten nicht eindeutig getrennt werden."));
        }

        String url = normalizeOptionalUrl(source.url(), warnings);
        Resolution resolution = resolve(firstToken, secondToken, participants, warnings);
        ImportPreviewStatus status = artist == null || title == null || resolution.participant() == null
                ? ImportPreviewStatus.INCOMPLETE : warnings.isEmpty() ? ImportPreviewStatus.READY : ImportPreviewStatus.WARNING;
        return new HistoricalImportPreviewLine(
                sourcePosition, sourceText, artist, title, url, firstToken, secondToken,
                resolution.participant() == null ? null : resolution.participant().participantId(),
                resolution.participant() == null ? null : resolution.participant().displayName(), status, List.copyOf(warnings), null, false
        );
    }

    private Resolution resolve(
            String firstToken, String secondToken, List<HistoricalImportParticipant> participants, List<ImportWarning> warnings
    ) {
        if (firstToken == null || secondToken == null) return new Resolution(null);
        List<HistoricalImportParticipant> firstParticipants = matchingParticipants(firstToken, participants);
        List<HistoricalImportParticipant> secondParticipants = matchingParticipants(secondToken, participants);
        String firstCountry = countryCodesByName.get(normalized(firstToken));
        String secondCountry = countryCodesByName.get(normalized(secondToken));
        HistoricalImportParticipant participant = null;
        String statedCountry = null;
        if (firstParticipants.size() == 1 && secondCountry != null && secondParticipants.isEmpty()) {
            participant = firstParticipants.getFirst(); statedCountry = secondCountry;
        } else if (secondParticipants.size() == 1 && firstCountry != null && firstParticipants.isEmpty()) {
            participant = secondParticipants.getFirst(); statedCountry = firstCountry;
        } else if (firstParticipants.size() == 1 && secondParticipants.isEmpty()) {
            participant = firstParticipants.getFirst();
            warnings.add(new ImportWarning("UNKNOWN_COUNTRY", "Das angegebene Land ist nicht eindeutig im lokalen Katalog bekannt."));
        } else if (secondParticipants.size() == 1 && firstParticipants.isEmpty()) {
            participant = secondParticipants.getFirst();
            warnings.add(new ImportWarning("UNKNOWN_COUNTRY", "Das angegebene Land ist nicht eindeutig im lokalen Katalog bekannt."));
        } else if (firstParticipants.size() > 1 || secondParticipants.size() > 1 || (!firstParticipants.isEmpty() && !secondParticipants.isEmpty())) {
            warnings.add(new ImportWarning("AMBIGUOUS_PARTICIPANT", "Die Teilnehmerzuordnung ist mehrdeutig und muss manuell gewählt werden."));
        } else {
            warnings.add(new ImportWarning("UNRESOLVED_PARTICIPANT", "Der Teilnehmer ist in dieser CSC-Ausgabe nicht eindeutig gepflegt."));
        }
        if (participant != null && statedCountry != null && !statedCountry.equals(participant.countryCode())) {
            warnings.add(new ImportWarning("COUNTRY_CONFLICT", "Das Quellenland passt nicht zur gepflegten Contest-Teilnahme; die Teilnehmerzuordnung wurde nicht geändert."));
        }
        return new Resolution(participant);
    }

    private static List<HistoricalImportParticipant> matchingParticipants(String token, List<HistoricalImportParticipant> participants) {
        String needle = normalized(token);
        return participants.stream().filter(participant -> normalized(participant.displayName()).equals(needle)
                || participant.aliases().stream().anyMatch(alias -> normalized(alias).equals(needle))).toList();
    }

    private static String normalizeOptionalUrl(String url, List<ImportWarning> warnings) {
        if (url == null || url.isBlank()) return null;
        try {
            URI uri = URI.create(compact(url));
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            warnings.add(new ImportWarning("INVALID_SOURCE_URL", "Der Quelllink muss eine HTTP- oder HTTPS-Adresse sein; er kann entfernt oder korrigiert werden."));
            return compact(url);
        }
    }

    private static boolean looksLikeEntry(String value) {
        return value != null && !value.isBlank() && (SONG_SEPARATOR.matcher(value).find() || ASSIGNMENT.matcher(value).matches());
    }

    private static String compact(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String normalized(String value) {
        return Normalizer.normalize(compact(value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String emptyToNull(String value) { return value.isEmpty() ? null : value; }
    private static String trimUrl(String value) { return value.replaceAll("[),.;]+$", ""); }
    private record SourceLine(String text, String url) { }
    private record Resolution(HistoricalImportParticipant participant) { }
}
