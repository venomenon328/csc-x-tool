package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryBareLinkedSongSuffixFormatTest {

    private static final Pattern MARKDOWN_LINE = Pattern.compile(
            "^\\[(.+)]\\((https?://.+)\\) \\*\\*(.+)\\*\\*$"
    );
    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void parsesTheFullBareLinkedSongSuffixFixture() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", FIXTURE, participants());

        assertThat(lines).hasSize(38);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("ABBA");
            assertThat(line.title()).isEqualTo("Fernando");
            assertThat(line.participantDisplayName()).isEqualTo("Worm");
            assertThat(line.countryToken()).isEqualTo("Liechtenstein");
            assertThat(line.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=dQsjAbZDx-4");
        });
        assertThat(lines.get(3)).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("EC3");
            assertThat(line.countryToken()).isEqualTo("Bosnien & Herzegowina");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
        assertThat(lines.get(33)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("The Police");
            assertThat(line.participantDisplayName()).isEqualTo("Peter Neururer");
            assertThat(line.countryToken()).isEqualTo("Saarland");
        });
        assertThat(lines.getLast()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Weezer");
            assertThat(line.title()).isEqualTo("Buddy Holly");
            assertThat(line.participantDisplayName()).isEqualTo("Red Forman");
        });
    }

    @Test
    void parsesTheRealBrowserClipboardShapeWithoutUnrecognizedPlaintextDuplicates() {
        BrowserClipboard clipboard = browserClipboard(FIXTURE);

        List<HistoricalImportPreviewLine> lines = parser.parse(clipboard.html(), clipboard.text(), participants());

        assertThat(lines).hasSize(38);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).extracting(ImportWarning::code).doesNotContain("UNRECOGNIZED_FORMAT");
        });
        assertThat(lines.getFirst().sourceText()).isEqualTo("ABBA - Fernando Liechtenstein - Worm");
        assertThat(lines.getFirst().youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=dQsjAbZDx-4");
        assertThat(lines.get(3).participantDisplayName()).isEqualTo("EC3");
    }

    private static BrowserClipboard browserClipboard(String source) {
        StringBuilder html = new StringBuilder("<div>");
        StringBuilder text = new StringBuilder();
        for (String rawLine : source.lines().filter(line -> !line.isBlank()).toList()) {
            Matcher matcher = MARKDOWN_LINE.matcher(rawLine.trim());
            assertThat(matcher.matches()).as("binding fixture line: %s", rawLine).isTrue();
            String song = matcher.group(1);
            String url = matcher.group(2).replace("\\&", "&");
            String assignment = matcher.group(3);
            html.append("<a href=\"")
                    .append(url.replace("&", "&amp;"))
                    .append("\">")
                    .append(song)
                    .append("</a>&nbsp;<strong>")
                    .append(assignment)
                    .append("</strong><br>");
            if (!text.isEmpty()) text.append('\n');
            text.append(song).append(' ').append(assignment);
        }
        html.append("</div>");
        return new BrowserClipboard(html.toString(), text.toString());
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Worm", "LI"), participant(2, "Toblerone Driver", "TR"),
                participant(3, "Eugene Fan", "BE"), participant(4, "EC3", "BA"),
                participant(5, "Wishmaster", "KZ"), participant(6, "Kingtoo", "IE"),
                participant(7, "Fingerinpo", "NR"), participant(8, "Joshi Judas Zwen", "KP"),
                participant(9, "Die Ente", "VA"), participant(10, "Grissom", "JP"),
                participant(11, "Dr. King Schultz", "KR"), participant(12, "Peyton's Royce", "AU"),
                participant(13, "Jaime Lannister", "DK"), participant(14, "Cameron Grimes", "EG"),
                participant(15, "Suicide", "FI"), participant(16, "Ravenous", "XS"),
                participant(17, "EdgeGF", "BS"), participant(18, "Jay Halstead", "PR"),
                participant(19, "McKlariato", "CN"), participant(20, "KlötenKlaus", "SE"),
                participant(21, "Legendk!ller", "IT"), participant(22, "Berggorilla", "UG"),
                participant(23, "Fletcher Cox", "LU"), participant(24, "Cortez", "NZ"),
                participant(25, "Barney Ross", "CH"), participant(26, "OMW", "WS"),
                participant(27, "Rated M", "BR"), participant(28, "Fabe", "CA"),
                participant(29, "Nick Heidfeld", "DE"), participant(30, "Straßenköter", "IS"),
                participant(31, "Mark Webber", "NO"), participant(32, "Kapitän Ahab", "US"),
                participant(33, "Clémentine Lyon", "FR"), participant(34, "Peter Neururer", "XL"),
                participant(35, "Daniel.", "NL"), participant(36, "Julian", "JM"),
                participant(37, "Asian Beckham", "GT"), participant(38, "Red Forman", "PT")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of());
    }

    private record BrowserClipboard(String html, String text) { }

    private static final String FIXTURE = """
            [ABBA - Fernando](https://www.youtube.com/watch?v=dQsjAbZDx-4) **Liechtenstein - Worm**
            [AnnenMayKantereit - Marie](https://www.youtube.com/watch?v=kpviEKrWysA) **Türkei - Toblerone Driver**
            [Annenmaykantereit - Pocahontas](https://www.youtube.com/watch?v=DraA3PUuoQc) **Belgien - Eugene Fan**
            [Barry Manilow - Mandy](https://www.youtube.com/watch?v=2XeSQVWleqY) **Bosnien & Herzegowina - EC3**
            [Behemoth - Bartzabel](https://www.youtube.com/watch?v=Dhfy9TPga-c) **Kasachstan - Wishmaster**
            [Blondie - Maria](https://www.youtube.com/watch?v=IwodQdM4hvk) **Irland - Kingtoo**
            [Bloodhound Gang - Foxtrot Uniform Charlie Kilo](https://www.youtube.com/watch?v=JZpxaiNV_sM) **Nauru - Fingerinpo**
            [Bowling For Soup - Alexa Bliss](https://youtu.be/K4vla140LEM) **Nordkorea - Joshi Judas Zwen**
            [Britney Spears - If U Seek Amy](https://www.youtube.com/watch?v=0aEnnH6t8Ts) **Vatikanstadt - Die Ente**
            [Casper - Michael X](https://www.youtube.com/watch?v=ekzx1T9-gPI) **Japan - Grissom**
            [Chiai Fujikawa - Laika](https://www.youtube.com/watch?v=xrHndmzfbMA) **Südkorea - Dr. King Schultz**
            [David Bowie - Ziggy Stardust](https://www.youtube.com/watch?v=7F9CorO3Glg) **Australien - Peyton's Royce**
            [Die Ärzte - Elke](https://www.youtube.com/watch?v=nPh8ynbm0Q8) **Dänemark - Jaime Lannister**
            [Feuerschwanz - Hier kommt Alex](https://m.youtube.com/watch?v=1z6U0HUKWQg) **Ägypten - Cameron Grimes**
            [Hearts At War - Ryan Ben O'Bay](https://www.youtube.com/watch?v=otECh38FDfg) **Finnland - Suicide**
            [IDLES - Danny Nedelko](https://youtu.be/QkF_G-RF66M) **Schottland - Ravenous**
            [Kaiser Chiefs - Ruby](https://www.youtube.com/watch?v=qObzgUfCl28) **Bahamas - EdgeGF**
            [Lady Gaga - Alejandro](https://youtu.be/niqrrmev4mA) **Puerto Rico - Jay Halstead**
            [Laura Branigan - Gloria](https://www.youtube.com/watch?v=nNEb2k_EmMg) **China - McKlariato**
            [Mark Forster - Natalie](https://www.youtube.com/watch?v=ChsFOQlFycA) **Schweden - KlötenKlaus**
            [Michael Jackson - Dirty Diana](https://www.youtube.com/watch?v=yUi_S6YWjZw) **Italien - Legendk!ller**
            [Mika - Grace Kelly](https://www.youtube.com/watch?v=0CGVgAYJyjk) **Uganda - Berggorilla**
            [Milk & Bone - Natalie](https://www.youtube.com/watch?v=wibDSxTck4g) **Luxemburg - Fletcher Cox**
            [Ozzy Osbourne - Mr. Crowley](https://www.youtube.com/watch?v=xMLFGtjYlO0) **Neuseeland - Cortez**
            [Patty Griffin - Mary](https://youtu.be/CLcRRolgffA) **Schweiz - Barney Ross**
            [Primus - Jerry was a racecar driver](https://www.youtube.com/watch?v=LBQ2305fLeA) **Samoa - OMW**
            [Rick Springfield - Jessie's Girl](https://www.youtube.com/watch?v=qYkbTyHXwbs) **Brasilien - Rated M**
            [Rob Cantor - Shia LaBeouf](https://www.youtube.com/watch?v=o0u4M6vppCI) **Kanada - Fabe**
            [Russ Ballard - Hey Bernadette](https://m.youtube.com/watch?v=J1JAQWnMIiE) **Deutschland - Nick Heidfeld**
            [Spiritbox - Constance](https://www.youtube.com/watch?v=mY_oDyqRM1A) **Island - Straßenköter**
            [Stick To Your Guns - Amber](https://www.youtube.com/watch?v=WrjibuzikUU) **Norwegen - Mark Webber**
            [Suzanne Vega - Luka](https://www.youtube.com/watch?v=VZt7J0iaUD0) **USA - Kapitän Ahab**
            [The Knack - My Sharona](https://youtu.be/8T3r1Y7p1mo) **Frankreich - Clémentine Lyon**
            [The Police - Roxanne](https://www.youtube.com/watch?v=3T1c7GkzRQQ) **Saarland - Peter Neururer**
            [Thees Uhlmann - Avicii](https://www.youtube.com/watch?v=j3sWGACXE7Y) **Niederlande - Daniel.**
            [Tom Petty And The Heartbreakers - Mary Jane's Last Dance](https://www.youtube.com/watch?v=aowSGxim_O8) **Jamaika - Julian**
            [Umberto Tozzi - Gloria](https://youtu.be/udvvVJYNpTk) **Guatemala - Asian Beckham**
            [Weezer - Buddy Holly](https://www.youtube.com/watch?v=Kjr7US2Z9aY) **Portugal - Red Forman**
            """;
}
