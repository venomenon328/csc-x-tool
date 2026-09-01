package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotVotingCommaFormatTest {

    private static final Pattern FIXTURE_LINE = Pattern.compile(
            "^\\*\\*(\\d+)\\. \\*\\*\\[\\*\\*(.+)\\*\\*]\\((https?://[^)]+)\\)\\*\\* \\(([^,]+), (.+)\\)\\*\\* – (\\d+) (\\S+)$"
    );
    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheFullGeneParmesanVotingFixtureWithCommaAssignments() {
        PublishedBallotPreviewBlock block = parse("", FIXTURE);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.displayName()).isEqualTo("Gene Parmesan");
        assertThat(block.countryCode()).isEqualTo("MX");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
        assertThat(block.positions()).allSatisfy(position -> assertThat(position.warnings()).isEmpty());
    }

    @Test
    void parsesEquivalentRichHtmlAndKeepsTheCorrectLinkPerLine() {
        PublishedBallotPreviewBlock block = parse(toRichHtml(FIXTURE), "");

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
    }

    @Test
    void keepsDisplayedPointsOpaqueAndRanksFromTheExplicitSequence() {
        String source = FIXTURE.replace("– 1 Punkt", "– 999 Fantasiepunkte");

        PublishedBallotPreviewBlock block = parse("", source);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions().getFirst().rank()).isEqualTo(15);
        assertThat(block.positions().getFirst().entryId()).isEqualTo(1);
    }

    @Test
    void stillRejectsAnExplicitRankSequenceThatIsNotFifteenDownToOne() {
        String source = FIXTURE.replace(
                "**14. **[**AnnenMayKantereit & Milky Chance – Roxanne**]",
                "**15. **[**AnnenMayKantereit & Milky Chance – Roxanne**]"
        );

        PublishedBallotPreviewBlock block = parse("", source);

        assertThat(block.status()).isEqualTo("WARNING");
        assertThat(block.warnings()).extracting(BallotImportWarning::code)
                .contains("EXPLICIT_RANK_SEQUENCE");
    }

    @Test
    void rejectsACommaSubmitterHintThatContradictsTheStoredEntry() {
        String source = FIXTURE.replace("(Samoa, ChickN-Stu)", "(Samoa, Rated M)");

        PublishedBallotPreviewBlock block = parse("", source);

        assertThat(block.status()).isEqualTo("INCOMPLETE");
        assertThat(block.positions().getFirst().entryId()).isNull();
        assertThat(block.warnings()).extracting(BallotImportWarning::code)
                .contains("SUBMITTER_CONFLICT");
    }

    private PublishedBallotPreviewBlock parse(String html, String text) {
        List<PublishedBallotPreviewBlock> blocks = parser.parse(html, text, participants(), entries(), Set.of());
        assertThat(blocks).hasSize(1);
        return blocks.getFirst();
    }

    private static String toRichHtml(String fixture) {
        StringBuilder html = new StringBuilder("<div><strong>3. Voting (Mexiko, Gene Parmesan):</strong><br>");
        fixture.lines().skip(1).filter(line -> !line.isBlank()).forEach(line -> {
            Matcher matcher = FIXTURE_LINE.matcher(line.trim());
            assertThat(matcher.matches()).as("fixture line: %s", line).isTrue();
            html.append("<strong>").append(matcher.group(1)).append(".&nbsp;</strong>")
                    .append("<a href=\"").append(escapeHtml(matcher.group(3))).append("\"><strong>")
                    .append(escapeHtml(matcher.group(2))).append("</strong></a>")
                    .append("<strong>&nbsp;(").append(escapeHtml(matcher.group(4))).append(", ")
                    .append(escapeHtml(matcher.group(5))).append(")</strong>&nbsp;–&nbsp;")
                    .append(matcher.group(6)).append(' ').append(escapeHtml(matcher.group(7))).append("<br>");
        });
        return html.append("</div>").toString();
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Gene Parmesan", "MX", "Mexiko"),
                participant(101, "ChickN-Stu", "WS", "Samoa"),
                participant(102, "Romelu Lukaku", "BE", "Belgien"),
                participant(103, "B.J. Penn", "IT", "Italien"),
                participant(104, "Jetlag", "AG", "Antigua und Barbuda"),
                participant(105, "OldManWaterfall", "KN", "St. Kitts und Nevis"),
                participant(106, "Wishmaster", "FI", "Finnland"),
                participant(107, "Eugene Fan", "DK", "Dänemark"),
                participant(108, "Rated M", "BR", "Brasilien"),
                participant(109, "Fletcher Cox", "NR", "Nauru"),
                participant(110, "Mark Webber", "NZ", "Neuseeland"),
                participant(111, "BBK", "NO", "Norwegen"),
                participant(112, "Asian Beckham", "SG", "Singapur"),
                participant(113, "Grissom", "JP", "Japan"),
                participant(114, "Erik Jendrisek", "FR", "Frankreich"),
                participant(115, "Charleen D. Ward", "AT", "Österreich")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode, String countryName) {
        return new PublishedBallotParticipant(id, id + 1_000, name, countryCode, countryName, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, "Iron & Wine", "Time After Time", "https://www.youtube.com/watch?v=5yKwYaq5Kf4", 101, "ChickN-Stu", "WS"),
                entry(2, "AnnenMayKantereit & Milky Chance", "Roxanne", "https://www.youtube.com/watch?v=VI4ssGtfdxw", 102, "Romelu Lukaku", "BE"),
                entry(3, "Bad Wolves", "Zombie", "https://www.youtube.com/watch?v=9XaS93WMRQQ", 103, "B.J. Penn", "IT"),
                entry(4, "Garbage & Screaming Females", "Because The Night", "https://www.youtube.com/watch?v=tOmKGjy-Ct0", 104, "Jetlag", "AG"),
                entry(5, "Leo Moracchioli feat. Rabea & Hannah", "Africa", "https://www.youtube.com/watch?v=MH9FyLsfDzw", 105, "OldManWaterfall", "KN"),
                entry(6, "In This Moment", "Call Me", "https://www.youtube.com/watch?v=u_XBRUY3Vqc", 106, "Wishmaster", "FI"),
                entry(7, "Volbeat", "I Only Wanna Be With You", "https://www.youtube.com/watch?v=gPKQKapxfs8", 107, "Eugene Fan", "DK"),
                entry(8, "Lenny Kravitz", "American Woman", "https://www.youtube.com/watch?v=5Z_fsdWYXMA", 108, "Rated M", "BR"),
                entry(9, "George Harrison", "Got My Mind Set on You", "https://www.youtube.com/watch?v=_71w4UA2Oxo", 109, "Fletcher Cox", "NR"),
                entry(10, "Josef Salvat", "Diamonds", "https://www.youtube.com/watch?v=_koFbsnw_PM", 110, "Mark Webber", "NZ"),
                entry(11, "Marvin Gaye", "I Heard It Through the Grapevine", "https://www.youtube.com/watch?v=hajBdDM2qdg", 111, "BBK", "NO"),
                entry(12, "Phil Collins", "You Can’t Hurry Love", "https://www.youtube.com/watch?v=upnrXooMh4s", 112, "Asian Beckham", "SG"),
                entry(13, "Ugly Kid Joe", "Cat’s in the Cradle", "https://www.youtube.com/watch?v=B32yjbCSVpU", 113, "Grissom", "JP"),
                entry(14, "Lucie Silvas", "Nothing Else Matters", "https://www.youtube.com/watch?v=QohUdrgbD2k", 114, "Erik Jendrisek", "FR"),
                entry(15, "Mark Ronson feat. Amy Winehouse", "Valerie", "https://www.youtube.com/watch?v=bixuI_GV5I0", 115, "Charleen D. Ward", "AT")
        );
    }

    private static PublishedBallotEntry entry(
            long id, String artist, String title, String url, long submitterParticipationId,
            String submitterName, String submitterCountryCode
    ) {
        return new PublishedBallotEntry(
                id, 1, artist, title, url, submitterParticipationId, submitterParticipationId + 1_000,
                submitterName, submitterCountryCode
        );
    }

    private static final String FIXTURE = """
            **3. Voting (Mexiko, Gene Parmesan):**
            **15. **[**Iron & Wine – Time After Time**](https://www.youtube.com/watch?v=5yKwYaq5Kf4)** (Samoa, ChickN-Stu)** – 1 Punkt
            **14. **[**AnnenMayKantereit & Milky Chance – Roxanne**](https://www.youtube.com/watch?v=VI4ssGtfdxw)** (Belgien, Romelu Lukaku)** – 2 Punkte
            **13. **[**Bad Wolves – Zombie**](https://www.youtube.com/watch?v=9XaS93WMRQQ)** (Italien, B.J. Penn)** – 3 Punkte
            **12. **[**Garbage & Screaming Females – Because The Night**](https://www.youtube.com/watch?v=tOmKGjy-Ct0)** (Antigua und Barbuda, Jetlag)** – 4 Punkte
            **11. **[**Leo Moracchioli feat. Rabea & Hannah – Africa**](https://www.youtube.com/watch?v=MH9FyLsfDzw)** (St. Kitts and Nevis, OldManWaterfall)** – 5 Punkte
            **10. **[**In This Moment – Call Me**](https://www.youtube.com/watch?v=u_XBRUY3Vqc)** (Finnland, Wishmaster)** – 6 Punkte
            **9. **[**Volbeat – I Only Wanna Be With You**](https://www.youtube.com/watch?v=gPKQKapxfs8)** (Dänemark, Eugene Fan)** – 7 Punkte
            **8. **[**Lenny Kravitz – American Woman**](https://www.youtube.com/watch?v=5Z_fsdWYXMA)** (Brasilien, Rated M)** – 8 Punkte
            **7. **[**George Harrison – Got My Mind Set on You**](https://www.youtube.com/watch?v=_71w4UA2Oxo)** (Nauru, Fletcher Cox)** – 9 Punkte
            **6. **[**Josef Salvat – Diamonds**](https://www.youtube.com/watch?v=_koFbsnw_PM)** (Neuseeland, Mark Webber)** – 10 Punkte
            **5. **[**Marvin Gaye – I Heard It Through the Grapevine**](https://www.youtube.com/watch?v=hajBdDM2qdg)** (Norwegen, BBK)** – 11 Punkte

            **4. **[**Phil Collins – You Can’t Hurry Love**](https://www.youtube.com/watch?v=upnrXooMh4s)** (Singapur, Asian Beckham)** – 13 Punkte

            **3. **[**Ugly Kid Joe – Cat’s in the Cradle**](https://www.youtube.com/watch?v=B32yjbCSVpU)** (Japan, Grissom)** – 16 Punkte

            **2. **[**Lucie Silvas – Nothing Else Matters**](https://www.youtube.com/watch?v=QohUdrgbD2k)** (Frankreich, Erik Jendrisek)** – 20 Punkte

            **1. **[**Mark Ronson feat. Amy Winehouse – Valerie**](https://www.youtube.com/watch?v=bixuI_GV5I0)** (Österreich, Charleen D. Ward)** – 25 Punkte
            """;
}
