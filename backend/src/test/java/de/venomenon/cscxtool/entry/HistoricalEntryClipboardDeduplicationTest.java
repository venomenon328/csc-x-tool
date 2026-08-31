package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryClipboardDeduplicationTest {

    private static final Pattern FORMAT_C_LINE = Pattern.compile("^\\*\\*(.*?) \\*\\*\\[(.+)]\\((https?://.+)\\)$");
    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void suppressesAllTwentyEightFlattenedPlaintextDuplicatesBehindStrongRichRows() {
        BrowserClipboard clipboard = browserClipboard(FORMAT_C);

        List<HistoricalImportPreviewLine> lines = parser.parse(clipboard.html(), clipboard.text(), participants());

        assertThat(lines).hasSize(28);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).startsWith("https://");
            assertThat(line.warnings()).extracting(ImportWarning::code).doesNotContain("UNRECOGNIZED_FORMAT");
        });
        assertThat(lines.getFirst()).satisfies(line -> {
            assertThat(line.sourceText()).isEqualTo("Australien - Mark Webber The Weeknd - Blinding Lights");
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/BkaNfAvPsyQ");
        });
        assertThat(lines.getLast()).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Die Ente");
            assertThat(line.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=6ZUIwj3FgUY");
        });
    }

    @Test
    void keepsAGenuineAdditionalUnknownPlaintextLineVisible() {
        BrowserClipboard clipboard = browserClipboard(FORMAT_C);

        List<HistoricalImportPreviewLine> lines = parser.parse(
                clipboard.html(), clipboard.text() + "\nBroken - source", participants()
        );

        assertThat(lines).hasSize(29);
        assertThat(lines.getLast()).satisfies(line -> {
            assertThat(line.sourceText()).isEqualTo("Broken - source");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("UNRECOGNIZED_FORMAT");
        });
    }

    private static BrowserClipboard browserClipboard(String source) {
        StringBuilder html = new StringBuilder("<div>");
        StringBuilder text = new StringBuilder();
        for (String rawLine : source.lines().filter(line -> !line.isBlank()).toList()) {
            Matcher matcher = FORMAT_C_LINE.matcher(rawLine.trim());
            assertThat(matcher.matches()).as("binding fixture line: %s", rawLine).isTrue();
            String assignment = matcher.group(1);
            String song = matcher.group(2);
            String url = matcher.group(3);
            html.append("<strong>").append(assignment).append("&nbsp;</strong><a href=\"")
                    .append(url.replace("&", "&amp;"))
                    .append("\">").append(song).append("</a><br>");
            if (!text.isEmpty()) text.append('\n');
            text.append(assignment).append(' ').append(song);
        }
        html.append("</div>");
        return new BrowserClipboard(html.toString(), text.toString());
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Mark Webber", "AU"), participant(2, "Scott D'Amore", "BA"),
                participant(3, "Rated M", "BR"), participant(4, "McKlariato", "CN"),
                participant(5, "KlötenKlaus", "CR"), participant(6, "Worm", "DE"),
                participant(7, "Cortez", "FI"), participant(8, "Everton", "GR"),
                participant(9, "Ravenous", "GU"), participant(10, "Serhou Guirassy", "JM"),
                participant(11, "Grissom", "JP"), participant(12, "PrettyFlamingo", "CG"),
                participant(13, "Kenny Ospreay", "LU"), participant(14, "Roman Reigns", "MT"),
                participant(15, "Straßenköter", "MX"), participant(16, "Kingtoo", "MN"),
                participant(17, "Fletcher Cox", "NR"), participant(18, "Jamie Hayter", "NZ"),
                participant(19, "Daniel.", "NL"), participant(20, "The Red-NGA Shankmos", "NG"),
                participant(21, "OMW", "WS"), participant(22, "snaggletooth", "XS"),
                participant(23, "Clementine Lyon", "CH", "Clémentine Lyon"), participant(24, "Contiomagus", "ZA"),
                participant(25, "Dr. King Schultz", "KR"), participant(26, "Toblerone Driver", "TR"),
                participant(27, "Berggorilla", "UG"), participant(28, "Die Ente", "VA")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode, String... aliases) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of(aliases));
    }

    private record BrowserClipboard(String html, String text) { }

    private static final String FORMAT_C = """
            **Australien - Mark Webber **[The Weeknd - Blinding Lights](https://youtu.be/BkaNfAvPsyQ)
            **Bosnien und Herzegowina - Scott D'Amore **[P!nk - Get The Party Started](https://www.youtube.com/watch?v=mW1dbiD_zDk)
            **Brasilien - Rated M **[Earth, Wind & Fire - Boogie Wonderland](https://www.youtube.com/watch?v=god7hAPv8f0)
            **China - McKlariato **[Sunstroke Project & Olia Tira - Run Away](https://www.youtube.com/watch?v=pHXDMe6QV-U)
            **Costa Rica - KlötenKlaus **[Electric Callboy - Everytime We Touch](https://www.youtube.com/watch?v=AuBXeF5acqE)
            **Deutschland - Worm **[R.I.O feat. U-Jean - Summer Jam](https://www.youtube.com/watch?v=figYwSRDpRE)
            **Finnland - Cortez **[Turnstile - Holiday](https://www.youtube.com/watch?v=D6yaJur9JUE)
            **Griechenland - Everton **[Elton John - I'm Still Standing](https://www.youtube.com/watch?v=ZHwVBirqD2s)
            **Guam - Ravenous **[Erik Cohen - Club Pinasse](https://www.youtube.com/watch?v=QwzG_hcXSPQ)
            **Jamaica - Serhou Guirassy **[Tarrus Riley - My Day](https://www.youtube.com/watch?v=xPg_e_3cK-E)
            **Japan - Grissom **[Mando Diao - Long Before Rock 'n' Roll](https://www.youtube.com/watch?v=1pZg7oAyyRk)
            **Kongo - PrettyFlamingo **[Millencolin - Da Strike](https://www.youtube.com/watch?v=4ixu8gOg948)
            **Luxemburg - Kenny Ospreay **[Linkin Park - Somewhere I Belong](https://www.youtube.com/watch?v=zsCD5XCu6CM)
            **Malta - Roman Reigns **[Snoop Dogg & Wiz Khalifa feat. Bruno Mars - Young, Wild & Free](https://www.youtube.com/watch?v=Wa5B22KAkEk)
            **Mexiko - Straßenköter **[Miley Cyrus - Flowers](https://www.youtube.com/watch?v=G7KNmW9a75Y)
            **Mongolei - Kingtoo **[Maitre Gims - Est-ce que tu m'aimes?](https://www.youtube.com/watch?v=6TpyRE_juyA)
            **Nauru - Fletcher Cox **[Nik Kershaw - The Riddle](https://www.youtube.com/watch?v=bDygS0a6Tgo)
            **Neuseeland - Jamie Hayter **[S Club - Bring It All Back](https://www.youtube.com/watch?v=GLQ0biK-ZgA)
            **Niederlande - Daniel. **[Adam Green - Emily](https://www.youtube.com/watch?v=6tepp9Zt7u8)
            **Nigeria - The Red-NGA Shankmos **[Red Hot Chilli Peppers - One Way Traffic](https://www.youtube.com/watch?v=zXNy-Osk1ek)
            **Samoa - OMW **[Farin Urlaub Racing Team - Am Strand](https://www.youtube.com/watch?v=d3cKuM2vwcs)
            **Schottland - snaggletooth **[Gloria Gaynor - I Will Survive](https://www.youtube.com/watch?v=6dYWe1c3OyU)
            **Schweiz - Clémentine Lyon **[KONGOS - Come with Me Now](https://www.youtube.com/watch?v=Gz2GVlQkn4Q)
            **Südafrika - Contiomagus **[Roger Whittaker - Ein bisschen Aroma](https://www.youtube.com/watch?v=n_HBRpOz3z0)
            **Südkorea - Dr. King Schultz **[Goldfinger - Superman](https://www.youtube.com/watch?v=h0rSYEoBMYM)
            **Türkei - Toblerone Driver **[Billie Eilish - Everything I Wanted](https://www.youtube.com/watch?v=EgBJmlPo8Xw)
            **Uganda - Berggorilla **[Eddie Murphy - Party All The Time](https://www.youtube.com/watch?v=iWa-6g-TbgI)
            **Vatikanstadt - Die Ente **[IVE - I AM](https://www.youtube.com/watch?v=6ZUIwj3FgUY)
            """;
}
