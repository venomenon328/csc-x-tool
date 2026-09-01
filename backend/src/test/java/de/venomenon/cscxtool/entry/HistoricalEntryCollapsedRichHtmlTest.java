package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryCollapsedRichHtmlTest {

    private static final Pattern MARKDOWN_LINE = Pattern.compile(
            "^\\[(.+)]\\((https?://[^)]+)\\)\\s*\\*\\*(\\(.+\\))\\*\\*$"
    );
    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void parsesCollapsedRichHtmlWithoutBreaksAndDeduplicatesThePlaintextRepresentation() {
        BrowserClipboard clipboard = collapsedBrowserClipboard(FIXTURE);

        List<HistoricalImportPreviewLine> lines = parser.parse(clipboard.html(), clipboard.text(), participants());

        assertThat(lines).hasSize(38);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines).flatExtracting(HistoricalImportPreviewLine::warnings)
                .extracting(ImportWarning::code)
                .doesNotContain("AMBIGUOUS_LINKS", "REPRESENTATION_CONFLICT", "UNRECOGNIZED_FORMAT");
        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Adel Tawil");
            assertThat(line.title()).isEqualTo("Ist da jemand");
            assertThat(line.participantDisplayName()).isEqualTo("Jay Halstead");
            assertThat(line.countryToken()).isEqualTo("Puerto Rico");
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/EkWjaoH7k6w");
        });
        assertThat(lines.getLast()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Wolfgang Petry");
            assertThat(line.participantDisplayName()).isEqualTo("Fletcher Cox");
            assertThat(line.countryToken()).isEqualTo("Luxemburg");
        });
    }

    @Test
    void acceptsOneSidedWhitespaceAroundTheAssignmentSeparatorButNotNoWhitespaceAtAll() {
        List<HistoricalImportParticipant> participants = List.of(participant(1, "Jay Halstead", "PR"));

        List<HistoricalImportPreviewLine> leftMissing = parser.parse(
                "", "[Artist - Title](https://example.test/song) **(Puerto Rico- Jay Halstead)**", participants
        );
        List<HistoricalImportPreviewLine> noWhitespace = parser.parse(
                "", "[Artist - Title](https://example.test/song) **(Puerto Rico-Jay Halstead)**", participants
        );

        assertThat(leftMissing).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantDisplayName()).isEqualTo("Jay Halstead");
            assertThat(line.countryToken()).isEqualTo("Puerto Rico");
        });
        assertThat(noWhitespace).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("UNRECOGNIZED_FORMAT");
        });
    }

    @Test
    void keepsARealUnstructuredMultiLinkBlockAmbiguous() {
        String html = """
                <div><a href="https://example.test/one">Artist One - Title One</a> freie Notiz
                <a href="https://example.test/two">Artist Two - Title Two</a> weitere Notiz</div>
                """;

        List<HistoricalImportPreviewLine> lines = parser.parse(html, "", participants());

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("AMBIGUOUS_LINKS");
        });
    }

    private static BrowserClipboard collapsedBrowserClipboard(String source) {
        StringBuilder html = new StringBuilder("<div>");
        StringBuilder text = new StringBuilder();
        for (String rawLine : source.lines().filter(line -> !line.isBlank()).toList()) {
            Matcher matcher = MARKDOWN_LINE.matcher(rawLine.trim());
            assertThat(matcher.matches()).as("binding fixture line: %s", rawLine).isTrue();
            String song = matcher.group(1);
            String url = matcher.group(2);
            String assignment = matcher.group(3);
            html.append("<a href=\"")
                    .append(url.replace("&", "&amp;"))
                    .append("\">").append(song).append("</a>&nbsp;<strong>")
                    .append(assignment).append("</strong>&nbsp;");
            if (!text.isEmpty()) text.append('\n');
            text.append(song.strip()).append(' ').append(assignment);
        }
        html.append("</div>");
        return new BrowserClipboard(html.toString(), text.toString());
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Jay Halstead", "PR"), participant(2, "Fabe", "CA"),
                participant(3, "Rated M", "BR"), participant(4, "OMW", "WS"),
                participant(5, "Barney Ross", "CH"), participant(6, "Worm", "LI"),
                participant(7, "Berggorilla", "UG"), participant(8, "EdgeGF", "BS"),
                participant(9, "Eugene Fan", "BE"), participant(10, "Fingerinpo", "NR"),
                participant(11, "Red Forman", "PT"), participant(12, "Die Ente", "VA"),
                participant(13, "Jaime Lannister", "DK"), participant(14, "Ravenous", "XS"),
                participant(15, "Wishmaster", "KZ"), participant(16, "Suicide", "FI"),
                participant(17, "Peyton's Royce", "AU"), participant(18, "Toblerone Driver", "TR"),
                participant(19, "Straßenköter", "IS"), participant(20, "Daniel.", "NL"),
                participant(21, "Joshi Judas Zwen", "KP"), participant(22, "Julian", "JM"),
                participant(23, "Mark Webber", "NO"), participant(24, "Legendk!ller", "IT"),
                participant(25, "Peter Neururer", "XL"), participant(26, "Kingtoo", "IE"),
                participant(27, "Cameron Grimes", "EG"), participant(28, "Nick Heidfeld", "DE"),
                participant(29, "Cortez", "NZ"), participant(30, "Kapitän Ahab", "US"),
                participant(31, "Dr. King Schultz", "KR"), participant(32, "EC3", "BA"),
                participant(33, "KlötenKlaus", "SE"), participant(34, "Asian Beckham", "GT"),
                participant(35, "McKlariato", "CN"), participant(36, "Clémentine Lyon", "FR"),
                participant(37, "Grissom", "JP"), participant(38, "Fletcher Cox", "LU")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of());
    }

    private record BrowserClipboard(String html, String text) { }

    private static final String FIXTURE = """
            [Adel Tawil - Ist da jemand](https://youtu.be/EkWjaoH7k6w)**(Puerto Rico -Jay Halstead)**
            [Apache 207 - ROLLER](https://www.youtube.com/watch?v=Fo3DAhiNKQo) **(Kanada - Fabe)**
            [Blümchen - Herz an Herz](https://www.youtube.com/watch?v=eGUsqIPurNQ) **(Brasilien - Rated M)**
            [Bodo Wartke - Meine neue Freundin](https://www.youtube.com/watch?v=ykRtu3bNppM&ab_channel=basimelia) **(Samoa - OMW)**
            [Böhse Onkelz - Bin ich nur glücklich wenn es schmerzt](https://youtu.be/A3MFQUmujsA) **(Schweiz - Barney Ross)**
            [Cassandra Steen ft. Adel Tawil - Stadt](https://www.youtube.com/watch?v=ltQNeihjqB0) **(Liechtenstein - Worm)**
            [Dame - Auf die guten alten Zeiten](https://www.youtube.com/watch?v=c3rLrFC8igY) **(Uganda - Berggorilla)**
            [Danger Dan - Das ist alles von der Kunstfreiheit gedeckt](https://www.youtube.com/watch?v=Y-B0lXnierw) **(Bahamas - EdgeGF)**
            [Die Ärzte - Lied vom Scheitern](https://www.youtube.com/watch?v=ZQDI-8YfzWQ) **(Belgien - Eugene Fan)**
            [Die Fantastischen Vier - Troy](https://www.youtube.com/watch?v=45RWmpxGVh8) **(Nauru - Fingerinpo)**
            [Die Toten Hosen - Willkommen in Deutschland](https://www.youtube.com/watch?v=8eU5HPgCAyo) **(Portugal - Red Forman)**
            [Farin Urlaub - Sonne](https://www.youtube.com/watch?v=1gt2enzkJvo) **(Vatikanstadt - Die Ente)**
            [Feine Sahne Fischfilet - Warten auf das Meer](https://www.youtube.com/watch?v=2gFV-YVhJjg) **(Dänemark - Jaime Lannister)**
            [Großstadtgeflüster - Ich rollator mit meim Besten](https://youtu.be/tCwG6gCiNZI) **(Schottland - Ravenous)**
            [HÄMATOM ft. Micha Rhein – Alte Liebe Rostet Nicht](https://www.youtube.com/watch?v=J618RrCqTHk) **(Kasachstan - Wishmaster)**
            [Heinz Rudolf Kunze - Finden Sie Mabel](https://www.youtube.com/watch?v=7SPd18hNBkY) **(Finnland - Suicide)**
            [Herbert Grönemeyer - Der Weg](https://www.youtube.com/watch?v=xSWJBClrmgo) **(Australien - Peyton's Royce)**
            [Jennifer Rostock - Haarspray](https://www.youtube.com/watch?v=MfVqeUjz1_o) **(Türkei - Toblerone Driver)**
            [Juju feat. Henning May - Vermissen](https://www.youtube.com/watch?v=YHbYAUs9JCo) **(Island - Straßenköter)**
            [Kapelle Petra - An irgendeinem Tag wird die Welt untergehen](https://www.youtube.com/watch?v=Em-DAHKEpA8) **(Niederlande - Daniel.)**
            [Madeline Juno - Obsolet](https://www.youtube.com/watch?v=LIzpV1PtAac) **(Nordkorea - Joshi Judas Zwen)**
            [Mono & Nikitaman – H*tler muss immer wieder sterben](https://www.youtube.com/watch?v=B_doMTCCflU&list=PLopphKUBSiKzwAuutClIIgyk6ZuqdB5KS) **(Jamaika - Julian)**
            [Moop Mama - Liebe](https://www.youtube.com/watch?v=N6xd8542AVg) **(Norwegen - Mark Webber)**
            [Peter Fox - Stadtaffe](https://www.youtube.com/watch?v=idbtj_hgEQg) **(Italien - Legendk!ller)**
            [Peter Maffay - Sonne in der Nacht](https://www.youtube.com/watch?v=bQ9MpKgZ8zg&ab_channel=herasle) **(Saarland - Peter Neururer)**
            [Philipp Dittberner & Marc - So Kaputt](https://www.youtube.com/watch?v=mD4wPM2xfhs)**(Irland - Kingtoo)**
            [Pizzeria & Jaus - Eine ins Leben](https://www.youtube.com/watch?v=8M6LYarAnRQ) **(Ägypten - Cameron Grimes)**
            [PULS - Kämpferherz](https://www.youtube.com/watch?v=is8qoXN8Wzk) **(Deutschland - Nick Heidfeld)**
            [Rammstein - Mann gegen Mann](https://www.youtube.com/watch?v=_EVKy35L7MM)**(Neuseeland - Cortez)**
            [Reinhard Fendrich - Tango Korrupti](https://www.youtube.com/watch?v=NhoEQLE0IvY) **(USA - Kapitän Ahab)**
            [Rika Kobayashi – Bios](https://www.youtube.com/watch?v=UMCka_vXtL4) **(Südkorea - Dr. King Schultz)**
            [Sarah Connor - Vincent](https://www.youtube.com/watch?v=qkrrqTEH_zg) **(Bosnien und Herzegowina - EC3)**
            [Silbermond - Symphonie](https://m.youtube.com/watch?v=deDquONycuA) **(Schweden - KlötenKlaus)**
            [Tic Tac Toe - Ich find dich scheiße](https://www.youtube.com/watch?v=P7HyGa2YFg4) **(Guatemala - Asian Beckham)**
            [Tic Tac Toe - Mr. Wichtig](https://www.youtube.com/watch?v=5VT-p5wYcb0) **(China - McKlariato)**
            [Versengold - Haut mir kein' Stein](https://youtu.be/Xy1yHLZpiHM) **(Frankreich - Clémentine Lyon)**
            [Wanda - Columbo](https://www.youtube.com/watch?v=FPvVZG9hlVY) **(Japan - Grissom)**
            [Wolfgang Petry - Wir sind das Ruhrgebiet](https://www.youtube.com/watch?v=n5Q2b67RwNc) **(Luxemburg - Fletcher Cox)**
            """;
}
