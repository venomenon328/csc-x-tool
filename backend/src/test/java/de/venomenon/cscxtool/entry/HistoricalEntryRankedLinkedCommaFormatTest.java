package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryRankedLinkedCommaFormatTest {

    private static final Pattern FIXTURE_LINE = Pattern.compile(
            "^1\\. \\[(.+)]\\((https?://[^)]+)\\) \\(([^,]+), (.+)\\) – xx Punkte$"
    );
    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheFullNumberedLinkedCommaFixtureWithoutListOrScoreDecoration() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", FIXTURE, participants());

        assertThat(lines).hasSize(30);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.artist()).doesNotStartWith("1.");
            assertThat(line.artist()).doesNotContain("xx Punkte");
            assertThat(line.title()).doesNotContain("xx Punkte");
            assertThat(line.participantToken()).doesNotContain("xx Punkte");
            assertThat(line.countryToken()).doesNotContain("xx Punkte");
            assertThat(line.warnings()).isEmpty();
        });

        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Frank Sinatra");
            assertThat(line.title()).isEqualTo("Have Yourself a Merry Little Christmas");
            assertThat(line.participantDisplayName()).isEqualTo("Julian");
            assertThat(line.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=sHVIVNoIPVM");
        });
        assertThat(lines.get(6)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Elmo ‘n’ Patsy");
            assertThat(line.participantDisplayName()).isEqualTo("OldManWaterfall");
            assertThat(line.countryToken()).isEqualTo("St. Kitts und Nevis");
        });
        assertThat(lines.get(7).title()).isEqualTo("Christmas Eve (Sarajevo 12/24)");
        assertThat(lines.get(11).title()).isEqualTo("Winter Wonderland/Don’t Worry Be Happy");
        assertThat(lines.get(18).title()).isEqualTo("‘Zat You, Santa Claus?");
        assertThat(lines.get(22).participantDisplayName()).isEqualTo("Peyton’s Royce");
        assertThat(lines.getLast().youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=JBjN7qVFcrY");
    }

    @Test
    void deduplicatesRealisticRichHtmlAndFlatPlaintextWhileKeepingEveryLink() {
        BrowserClipboard clipboard = browserClipboard(FIXTURE);

        List<HistoricalImportPreviewLine> lines = parser.parse(clipboard.html(), clipboard.text(), participants());

        assertThat(lines).hasSize(30);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).startsWith("http");
            assertThat(line.warnings()).isEmpty();
        });
        assertThat(lines.getFirst().sourceText())
                .isEqualTo("1. Frank Sinatra – Have Yourself a Merry Little Christmas (Jamaika, Julian) – xx Punkte");
        assertThat(lines.get(6).youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=MgIwLeASnkw");
        assertThat(lines.get(22).youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=6OPQmn75y3I");
    }

    private static BrowserClipboard browserClipboard(String source) {
        StringBuilder html = new StringBuilder("<div>");
        StringBuilder text = new StringBuilder();
        for (String rawLine : source.lines().filter(line -> !line.isBlank()).toList()) {
            Matcher matcher = FIXTURE_LINE.matcher(rawLine.trim());
            assertThat(matcher.matches()).as("binding fixture line: %s", rawLine).isTrue();
            String song = matcher.group(1);
            String url = matcher.group(2);
            String country = matcher.group(3);
            String participant = matcher.group(4);
            html.append("1.&nbsp;<a href=\"").append(url).append("\">")
                    .append(escapeHtml(song)).append("</a>&nbsp;(")
                    .append(escapeHtml(country)).append(", ").append(escapeHtml(participant))
                    .append(")&nbsp;–&nbsp;xx Punkte<br>");
            if (!text.isEmpty()) text.append('\n');
            text.append("1. ").append(song).append(" (").append(country).append(", ")
                    .append(participant).append(") – xx Punkte");
        }
        html.append("</div>");
        return new BrowserClipboard(html.toString(), text.toString());
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Julian", "JM"), participant(2, "Rated M", "BR"),
                participant(3, "Katerstrophe", "AT"), participant(4, "McKlariato", "CN"),
                participant(5, "Grissom", "JP"), participant(6, "ChickN-Stu", "WS"),
                participant(7, "OldManWaterfall", "KN"), participant(8, "Maxine Caulfield", "IE"),
                participant(9, "BBK", "NO"), participant(10, "Wishmaster", "FI"),
                participant(11, "Dale Cooper", "CA"), participant(12, "Ravenous", "XS"),
                participant(13, "Romelu Lukaku", "BE"), participant(14, "KlötenKlaus", "SE"),
                participant(15, "Mark Webber", "NZ"), participant(16, "Kapitän Ahab", "US"),
                participant(17, "Zwen", "KR"), participant(18, "Jetlag", "AG"),
                participant(19, "olisimpson22", "PL"), participant(20, "Berggorilla", "UG"),
                participant(21, "B.J. Penn", "IT"), participant(22, "Peter Neururer", "LU"),
                participant(23, "Peyton’s Royce", "AU"), participant(24, "Gene Parmesan", "MX"),
                participant(25, "Erik Jendrisek", "FR"), participant(26, "Eugene Fan", "DK"),
                participant(27, "Asian Beckham", "SG"), participant(28, "Fletcher Cox", "NR"),
                participant(29, "Nick Heidfeld", "MH"), participant(30, "Worm", "DE")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id + 1_000, name, countryCode, List.of());
    }

    private record BrowserClipboard(String html, String text) { }

    private static final String FIXTURE = """
            1. [Frank Sinatra – Have Yourself a Merry Little Christmas](https://www.youtube.com/watch?v=sHVIVNoIPVM) (Jamaika, Julian) – xx Punkte
            1. [Dean Martin – Let It Snow! Let It Snow! Let It Snow!](https://www.youtube.com/watch?v=o2uvtl-1V70) (Brasilien, Rated M) – xx Punkte
            1. [Mariah Carey – All I Want for Christmas Is You](https://www.youtube.com/watch?v=yXQViqx6GMY) (Österreich, Katerstrophe) – xx Punkte
            1. [The Killers – Don’t Shoot Me Santa](https://www.youtube.com/watch?v=cglLJJ0Czo8) (China, McKlariato) – xx Punkte
            1. [The Mighty Mighty Bosstones – Xmas Time](https://www.youtube.com/watch?v=94lcKzEX2Yk) (Japan, Grissom) – xx Punkte
            1. [John Williams – Somewhere In My Memory](https://www.youtube.com/watch?v=5kHH6LJpEbQ) (Samoa, ChickN-Stu) – xx Punkte
            1. [Elmo ‘n’ Patsy – Grandma Got Run Over by a Reindeer](https://www.youtube.com/watch?v=MgIwLeASnkw) (St. Kitts and Nevis, OldManWaterfall) – xx Punkte
            1. [Trans-Siberian Orchestra – Christmas Eve (Sarajevo 12/24)](https://www.youtube.com/watch?v=MHioIlbnS_A) (Irland, Maxine Caulfield) – xx Punkte
            1. [Peter Alexander – Stille Nacht, heilige Nacht](https://www.youtube.com/watch?v=oBPfl2nJUfk) (Norwegen, BBK) – xx Punkte
            1. [Steel Panther – The Stocking Song](https://www.youtube.com/watch?v=KmjHevZOC90) (Finnland, Wishmaster) – xx Punkte
            1. [Sarah McLachlan – River](https://www.youtube.com/watch?v=c5MAnwQp430) (Kanada, Dale Cooper) – xx Punkte
            1. [Pentatonix – Winter Wonderland/Don’t Worry Be Happy](https://www.youtube.com/watch?v=L1nQpoAvTSg) (Schottland, Ravenous) – xx Punkte
            1. [Ariana Grande – Santa Tell Me](https://www.youtube.com/watch?v=nlR0MkrRklg) (Belgien, Romelu Lukaku) – xx Punkte
            1. [Kelly Clarkson – Underneath the Tree](https://www.youtube.com/watch?v=YfF10ow4YEo) (Schweden, KlötenKlaus) – xx Punkte
            1. [Nat King Cole – The Christmas Song](https://www.youtube.com/watch?v=hwacxSnc4tI) (Neuseeland, Mark Webber) – xx Punkte
            1. [Boney M – Little Drummer Boy](https://www.youtube.com/watch?v=plGj8VRTqJE) (USA, Kapitän Ahab) – xx Punkte
            1. [FNC Artists – It’s Christmas](https://www.youtube.com/watch?v=qTVonIkMXpw) (Südkorea, Zwen) – xx Punkte
            1. [Harvey Milk – Death Goes to the Winner](https://www.youtube.com/watch?v=eZiB66BraPo) (Antigua und Barbuda, Jetlag) – xx Punkte
            1. [Louis Armstrong – ‘Zat You, Santa Claus?](https://www.youtube.com/watch?v=O3TXwWANFbM) (Polen, olisimpson22) – xx Punkte
            1. [Jona Lewie – Stop the Cavalry](https://www.youtube.com/watch?v=2HkJHApgKqw) (Uganda, Berggorilla) – xx Punkte
            1. [Poly Styrene – Black Christmas](https://www.youtube.com/watch?v=ML0cD0REC4Y) (Italien, B.J. Penn) – xx Punkte
            1. [Elton John – Cold as Christmas (In the Middle of the Year)](https://www.youtube.com/watch?v=Q1FREdwzxrg) (Luxemburg, Peter Neururer) – xx Punkte
            1. [Ellie Goulding – O Holy Night](https://www.youtube.com/watch?v=6OPQmn75y3I) (Australien, Peyton’s Royce) – xx Punkte
            1. [Paul McCartney – Wonderful Christmastime](https://www.youtube.com/watch?v=V9BZDpni56Y) (Mexiko, Gene Parmesan) – xx Punkte
            1. [Pentatonix – God Rest Ye Merry, Gentlemen](https://www.youtube.com/watch?v=ku7ohU1IGls) (Frankreich, Erik Jendrisek) – xx Punkte
            1. [Justin Bieber – Mistletoe](https://www.youtube.com/watch?v=LUjn3RpkcKY) (Dänemark, Eugene Fan) – xx Punkte
            1. [NSYNC – Merry Christmas, Happy Holidays](https://www.youtube.com/watch?v=oeLfuIzF6v8) (Singapur, Asian Beckham) – xx Punkte
            1. [Stefan Raab – Wir kiffen (Weihnachtsversion)](https://www.youtube.com/watch?v=t_U3sR5RspI) (Nauru, Fletcher Cox) – xx Punkte
            1. [Sia – Ho Ho Ho](https://www.youtube.com/watch?v=MGanJGGVSrw) (Marshallinseln, Nick Heidfeld) – xx Punkte
            1. [Bobby Helms – Jingle Bell Rock](https://www.youtube.com/watch?v=JBjN7qVFcrY) (Deutschland, Worm) – xx Punkte
            """;
}
