package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryLinkedSongSuffixFormatTest {

    private static final Pattern MARKDOWN_LINE = Pattern.compile(
            "^\\[(.+)]\\((https?://.+)\\) \\*\\*(\\(.+\\))\\*\\*$"
    );
    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void parsesTheFullLinkedSongSuffixFixtureWithCountriesParticipantsAndUrls() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", FIXTURE, participants());

        assertThat(lines).hasSize(36);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).isEmpty();
        });

        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Andreas Kümmert");
            assertThat(line.title()).isEqualTo("Simple Man");
            assertThat(line.participantDisplayName()).isEqualTo("Peter Neururer");
            assertThat(line.countryToken()).isEqualTo("Saarland");
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/Vbq7HygVMW4");
        });
        assertThat(lines.get(12)).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Kapitän Ahab");
            assertThat(line.countryToken()).isEqualTo("USA");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
        assertThat(lines.get(22)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Spiritbox");
            assertThat(line.title()).isEqualTo("Blessed Be");
        });
        assertThat(lines.get(30)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Viagra Boys");
            assertThat(line.title()).isEqualTo("Ain't Nice");
        });
        assertThat(lines.get(15).youtubeUrl()).isEqualTo(
                "https://www.youtube.com/watch?v=ms6UmbL48OI&ab_channel=KnallfroschElektro-Topic"
        );
        assertThat(lines.get(27).youtubeUrl()).isEqualTo(
                "https://www.youtube.com/watch?app=desktop&v=w-_JLKh6WBA&ab_channel=UMMEBLOCK"
        );
    }

    @Test
    void parsesEquivalentRichHtmlWithoutDependingOnMarkdownMarkers() {
        String html = """
                <p><a href="https://youtu.be/Vbq7HygVMW4">Andreas Kümmert - Simple Man</a>&nbsp;<strong>(Saarland - Peter Neururer)</strong></p>
                <p><a href="https://www.youtube.com/watch?v=vzWds5gWS6c">Viagra Boys — Ain't Nice</a>&nbsp;<strong>(Kanada - Fabe)</strong></p>
                """;

        List<HistoricalImportPreviewLine> lines = parser.parse(html, "", participants());

        assertThat(lines).hasSize(2).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines).extracting(HistoricalImportPreviewLine::participantDisplayName)
                .containsExactly("Peter Neururer", "Fabe");
        assertThat(lines).extracting(HistoricalImportPreviewLine::countryToken)
                .containsExactly("Saarland", "Kanada");
    }

    @Test
    void parsesTheRealVivaldiClipboardShapeAsThirtySixLinkedRowsWithoutDuplicates() {
        BrowserClipboard clipboard = browserClipboard(FIXTURE);

        List<HistoricalImportPreviewLine> lines = parser.parse(clipboard.html(), clipboard.text(), participants());

        assertThat(lines).hasSize(36);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.sourceText()).isEqualTo("Andreas Kümmert - Simple Man (Saarland - Peter Neururer)");
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/Vbq7HygVMW4");
        });
        assertThat(lines.getLast()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("XOV");
            assertThat(line.participantDisplayName()).isEqualTo("Berggorilla");
            assertThat(line.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=M7bUWuj0EYE&ab_channel=XOV");
        });
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
                participant(1, "Peter Neururer", "XL"), participant(2, "Mark Webber", "NO"),
                participant(3, "McKlariato", "CN"), participant(4, "Ravenous", "XS"),
                participant(5, "Barney Ross", "CH"), participant(6, "Joshi Judas Zwen", "KP"),
                participant(7, "KlötenKlaus", "SE"), participant(8, "Die Ente", "VA"),
                participant(9, "Grissom", "JP"), participant(10, "Fletcher Cox", "LU"),
                participant(11, "Daniel.", "NL"), participant(12, "Dr. King Schultz", "KR"),
                participant(13, "Kapitän Ahab", "US"), participant(14, "Toblerone Driver", "TR"),
                participant(15, "Julian", "JM"), participant(16, "Jaime Lannister", "DK"),
                participant(17, "Straßenköter", "IS"), participant(18, "Suicide", "FI"),
                participant(19, "Cameron Grimes", "EG"), participant(20, "Kingtoo", "IE"),
                participant(21, "EC3", "BA"), participant(22, "Rated M", "BR"),
                participant(23, "Wishmaster", "KZ"), participant(24, "Asian Beckham", "GT"),
                participant(25, "Legendk!ller", "IT"), participant(26, "Fingerinpo", "NR"),
                participant(27, "Clémentine Lyon", "FR"), participant(28, "Red Forman", "PT"),
                participant(29, "Cortez", "NZ"), participant(30, "EdgeGF", "BS"),
                participant(31, "Fabe", "CA"), participant(32, "Nick Heidfeld", "DE"),
                participant(33, "Eugene Fan", "BE"), participant(34, "AEWconic", "AU"),
                participant(35, "OMW", "WS"), participant(36, "Berggorilla", "UG")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of());
    }

    private record BrowserClipboard(String html, String text) { }

    private static final String FIXTURE = """
            [Andreas Kümmert - Simple Man](https://youtu.be/Vbq7HygVMW4) **(Saarland - Peter Neururer)**
            [Being As An Ocean - Alone](https://www.youtube.com/watch?v=Fikr0N0htRs) **(Norwegen - Mark Webber)**
            [Blind Channel - Dark Side](https://www.youtube.com/watch?v=JXEK-fc3_BU) **(China - McKlariato)**
            [Blues Pills - Devil Man](https://youtu.be/5TVyJxBtXy8) **(Schottland - Ravenous)**
            [Blues Saraceno - The River](https://www.youtube.com/watch?v=fmLR8S8DYqo) **(Schweiz - Barney Ross)**
            [BURSTERS - Colors](https://www.youtube.com/watch?v=KuSl9MhrAF8&) **(Nordkorea - Joshi Judas Zwen)**
            [Cat Ballou - Et jitt kein wood](https://m.youtube.com/watch?v=VF6p-BGQcl4) **(Schweden - KlötenKlaus)**
            [Corpse - Miss you](https://www.youtube.com/watch?v=clnIeauiVDM) **(Vatikanstadt - Die Ente)**
            [Dead Sara - Weatherman](https://www.youtube.com/watch?v=y5vr_Vhoumc) **(Japan - Grissom)**
            [Gigolo Aunts - Where I Found My Heaven](https://www.youtube.com/watch?v=dGyFtPEvYX8) **(Luxemburg - Fletcher Cox)**
            [Grillmaster Flash - Sottrum](https://www.youtube.com/watch?v=97lauD5N7QY) **(Niederlande - Daniel.)**
            [Hagane - WintrySky](https://www.youtube.com/watch?v=FLxibhGlTGQ) **(Südkorea - Dr. King Schultz)**
            [Holly Loose - John Maynard](https://www.youtube.com/watch?v=mLu_v7nHIcI) **(USA - Kapitän Ahab)**
            [Jack Curley - I'm Here For You](https://www.youtube.com/watch?v=EhDUn7q0_l8) **(Türkei - Toblerone Driver)**
            [Jake Isaac - Waiting Here](https://www.youtube.com/watch?v=cOQ3Zae8LSU) **(Jamaika - Julian)**
            [Knallfrosch Elektro - Mitten im Leben](https://www.youtube.com/watch?v=ms6UmbL48OI\\&ab_channel=KnallfroschElektro-Topic) **(Dänemark - Jaime Lannister)**
            [Listener - There's Money in the Walls](https://www.youtube.com/watch?v=sCqXflk5-d0) **(Island - Straßenköter)**
            [MakeWar - Oh, Brother](https://www.youtube.com/watch?v=CRXgOgFTSh0) **(Finnland - Suicide)**
            [Mathea - Chaos](https://www.youtube.com/watch?v=fMlo3nxVEUM) **(Ägypten - Cameron Grimes)**
            [Mattanja Joy Bradley - Hurricane](https://www.youtube.com/watch?v=3ffxz338S8M) **(Irland - Kingtoo)**
            [Muhabbet - Sie liegt in meinen Armen](https://www.youtube.com/watch?v=d0XZyUH5neo) **(Bosnien und Herzegowina - EC3)**
            [Rockstah - Highscore](https://www.youtube.com/watch?v=-_iUf-jfRXA) **(Brasilien - Rated M)**
            [Spiritbox – Blessed Be](https://www.youtube.com/watch?v=yht0WDdzGJM) **(Kasachstan - Wishmaster)**
            [Subbotnik - Rot](https://www.youtube.com/watch?v=JvwiSLwCxc0) **(Guatemala - Asian Beckham)**
            [The Roop - On Fire](https://www.youtube.com/watch?v=YFzcmH1kDj8) **(Italien - Legendk!ller)**
            [The Tragic Thrills - Fever](https://www.youtube.com/watch?v=CazO8Ar4Szg) **(Nauru - Fingerinpo)**
            [The Treatment - Bite Back](https://youtu.be/HsuOhGVmkdg) **(Frankreich - Clémentine Lyon)**
            [UMME BLOCK - Yellow Lights](https://www.youtube.com/watch?app=desktop\\&v=w-_JLKh6WBA\\&ab_channel=UMMEBLOCK) **(Portugal - Red Forman)**
            [Unprocessed - deadrose](https://www.youtube.com/watch?v=YnGRrWNOZ4E) **(Neuseeland - Cortez)**
            [Vandroya - Why Should We Say Goodbye](https://www.youtube.com/watch?v=W9pXKIYhTCM) **(Bahamas - EdgeGF)**
            [Viagra Boys — Ain't Nice](https://www.youtube.com/watch?v=vzWds5gWS6c) **(Kanada - Fabe)**
            [Victoria Justice - Treat Myself](https://www.youtube.com/watch?v=XSLIgUkrdio) **(Deutschland - Nick Heidfeld)**
            [Wally Warning - No Monkey](https://www.youtube.com/watch?v=z2Zr3EgzbEs) **(Belgien - Eugene Fan)**
            [We Are Temporary - You Can Now Let Go](https://www.youtube.com/watch?v=4efGQgC5pd4) **(Australien - AEWconic)**
            [Wind Rose - Diggy Diggy Hole](https://www.youtube.com/watch?v=34CZjsEI1yU) **(Samoa - OMW)**
            [XOV - Lucifer](https://www.youtube.com/watch?v=M7bUWuj0EYE\\&ab_channel=XOV) **(Uganda - Berggorilla)**
            """;
}
