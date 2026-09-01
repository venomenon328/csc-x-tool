package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PublishedBallotBareSubmitterSuffixTest {

    private static final Pattern FIXTURE_LINE = Pattern.compile(
            "^(\\d+)\\.\\s+\\[(.+)]\\((https?://[^)]+)\\)\\s+\\*\\*(.+?)\\s+\\*\\*(\\d+)\\s+(.+)$"
    );

    private final PublishedBallotImportParser parser = new PublishedBallotImportParser(
            new CountryCatalog(new ObjectMapper())
    );

    @Test
    void parsesTheRealAustraliaBallotWithBareCountryAndSubmitterSuffixes() {
        PublishedBallotPreviewBlock block = parse("", FIXTURE, entries());

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.participationId()).isEqualTo(100);
        assertThat(block.displayName()).isEqualTo("Peyton's Royce");
        assertThat(block.countryCode()).isEqualTo("AU");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).hasSize(15);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::submitterDisplayName)
                .containsExactly(
                        "Eugene Fan", "Legendk!ller", "Worm", "Fletcher Cox", "Fingerinpo",
                        "Jaime Lannister", "EdgeGF", "Julian", "Berggorilla", "Mark Webber",
                        "Red Forman", "Grissom", "Peter Neururer", "Jay Halstead", "Kingtoo"
                );
        assertThat(block.positions()).allSatisfy(position -> assertThat(position.warnings()).isEmpty());
    }

    @Test
    void parsesTheEquivalentRichHtmlAndKeepsEachUrlOnItsOwnSong() {
        PublishedBallotPreviewBlock block = parse(toRichHtml(FIXTURE), "", entries());

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.warnings()).isEmpty();
        assertThat(block.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 15).mapToObj(Long::valueOf).toList());
    }

    @Test
    void keepsAnIncorrectBareSubmitterHintAsBlockingConflict() {
        String source = FIXTURE.replace("Belgien - Eugene Fan", "Belgien - Fabe");

        PublishedBallotPreviewBlock block = parse("", source, entries());

        assertThat(block.status()).isEqualTo("INCOMPLETE");
        assertThat(block.warnings()).extracting(BallotImportWarning::code).contains("SUBMITTER_CONFLICT");
        assertThat(block.positions().getFirst().entryId()).isNull();
        assertThat(block.positions().getFirst().warnings()).extracting(BallotImportWarning::code)
                .contains("SUBMITTER_CONFLICT");
    }

    @Test
    void doesNotSplitAHyphenInsideTheResolvedSongTitleAsSubmitterMetadata() {
        String source = FIXTURE.replace(
                "[Annenmaykantereit - Pocahontas]",
                "[Annenmaykantereit - Pocahontas - Live Cut]"
        );
        List<PublishedBallotEntry> updated = new ArrayList<>(entries());
        updated.set(0, entry(
                1, "Annenmaykantereit", "Pocahontas - Live Cut",
                "https://www.youtube.com/watch?v=DraA3PUuoQc", 101, "Eugene Fan", "BE"
        ));

        PublishedBallotPreviewBlock block = parse("", source, updated);

        assertThat(block.status()).isEqualTo("READY");
        assertThat(block.positions().getFirst().entryId()).isEqualTo(1);
        assertThat(block.positions().getFirst().submitterDisplayName()).isEqualTo("Eugene Fan");
        assertThat(block.positions().getFirst().warnings()).isEmpty();
    }

    private PublishedBallotPreviewBlock parse(String html, String text, List<PublishedBallotEntry> entries) {
        List<PublishedBallotPreviewBlock> blocks = parser.parse(html, text, participants(), entries, Set.of());
        assertThat(blocks).hasSize(1);
        return blocks.getFirst();
    }

    private static String toRichHtml(String fixture) {
        StringBuilder html = new StringBuilder("<div><strong>[#1 Australien - Peyton's Royce]</strong><br>");
        fixture.lines().skip(1).filter(line -> !line.isBlank()).forEach(line -> {
            Matcher matcher = FIXTURE_LINE.matcher(line);
            assertThat(matcher.matches()).as("fixture line: %s", line).isTrue();
            html.append(matcher.group(1)).append(".&nbsp;<a href=\"")
                    .append(matcher.group(3).replace("&", "&amp;"))
                    .append("\">").append(matcher.group(2)).append("</a>&nbsp;<strong>")
                    .append(matcher.group(4)).append("&nbsp;</strong>")
                    .append(matcher.group(5)).append(' ').append(matcher.group(6)).append("<br>");
        });
        return html.append("</div>").toString();
    }

    private static List<PublishedBallotParticipant> participants() {
        return List.of(
                participant(100, "Peyton's Royce", "AU", "Australien"),
                participant(101, "Eugene Fan", "BE", "Belgien"),
                participant(102, "Legendk!ller", "IT", "Italien"),
                participant(103, "Worm", "LI", "Liechtenstein"),
                participant(104, "Fletcher Cox", "LU", "Luxemburg"),
                participant(105, "Fingerinpo", "NR", "Nauru"),
                participant(106, "Jaime Lannister", "DK", "Dänemark"),
                participant(107, "EdgeGF", "BS", "Bahamas"),
                participant(108, "Julian", "JM", "Jamaika"),
                participant(109, "Berggorilla", "UG", "Uganda"),
                participant(110, "Mark Webber", "NO", "Norwegen"),
                participant(111, "Red Forman", "PT", "Portugal"),
                participant(112, "Grissom", "JP", "Japan"),
                participant(113, "Peter Neururer", "XL", "Saarland"),
                participant(114, "Jay Halstead", "PR", "Puerto Rico"),
                participant(115, "Kingtoo", "IE", "Irland")
        );
    }

    private static PublishedBallotParticipant participant(long id, String name, String countryCode, String countryName) {
        return new PublishedBallotParticipant(id, id + 1_000, name, countryCode, countryName, List.of());
    }

    private static List<PublishedBallotEntry> entries() {
        return List.of(
                entry(1, "Annenmaykantereit", "Pocahontas", "https://www.youtube.com/watch?v=DraA3PUuoQc", 101, "Eugene Fan", "BE"),
                entry(2, "Michael Jackson", "Dirty Diana", "https://www.youtube.com/watch?v=yUi_S6YWjZw", 102, "Legendk!ller", "IT"),
                entry(3, "ABBA", "Fernando", "https://www.youtube.com/watch?v=dQsjAbZDx-4", 103, "Worm", "LI"),
                entry(4, "Milk & Bone", "Natalie", "https://www.youtube.com/watch?v=wibDSxTck4g", 104, "Fletcher Cox", "LU"),
                entry(5, "Bloodhound Gang", "Foxtrot Uniform Charlie Kilo", "https://www.youtube.com/watch?v=JZpxaiNV_sM", 105, "Fingerinpo", "NR"),
                entry(6, "Die Ärzte", "Elke", "https://www.youtube.com/watch?v=nPh8ynbm0Q8", 106, "Jaime Lannister", "DK"),
                entry(7, "Kaiser Chiefs", "Ruby", "https://www.youtube.com/watch?v=qObzgUfCl28", 107, "EdgeGF", "BS"),
                entry(8, "Tom Petty And The Heartbreakers", "Mary Jane's Last Dance", "https://www.youtube.com/watch?v=aowSGxim_O8", 108, "Julian", "JM"),
                entry(9, "Mika", "Grace Kelly", "https://www.youtube.com/watch?v=0CGVgAYJyjk", 109, "Berggorilla", "UG"),
                entry(10, "Stick To Your Guns", "Amber", "https://www.youtube.com/watch?v=WrjibuzikUU", 110, "Mark Webber", "NO"),
                entry(11, "Weezer", "Buddy Holly", "https://www.youtube.com/watch?v=Kjr7US2Z9aY", 111, "Red Forman", "PT"),
                entry(12, "Casper", "Michael X", "https://www.youtube.com/watch?v=ekzx1T9-gPI", 112, "Grissom", "JP"),
                entry(13, "The Police", "Roxanne", "https://www.youtube.com/watch?v=3T1c7GkzRQQ", 113, "Peter Neururer", "XL"),
                entry(14, "Lady Gaga", "Alejandro", "https://youtu.be/niqrrmev4mA", 114, "Jay Halstead", "PR"),
                entry(15, "Blondie", "Maria", "https://www.youtube.com/watch?v=IwodQdM4hvk", 115, "Kingtoo", "IE")
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
            **[#1 Australien - Peyton's Royce]**
            15. [Annenmaykantereit - Pocahontas](https://www.youtube.com/watch?v=DraA3PUuoQc) **Belgien - Eugene Fan **1 point
            14. [Michael Jackson - Dirty Diana](https://www.youtube.com/watch?v=yUi_S6YWjZw) **Italien - Legendk!ller **2 points
            13. [ABBA - Fernando](https://www.youtube.com/watch?v=dQsjAbZDx-4) **Liechtenstein - Worm **3 points
            12. [Milk & Bone - Natalie](https://www.youtube.com/watch?v=wibDSxTck4g) **Luxemburg - Fletcher Cox **4 points
            11. [Bloodhound Gang - Foxtrot Uniform Charlie Kilo](https://www.youtube.com/watch?v=JZpxaiNV_sM) **Nauru - Fingerinpo **5 points
            10. [Die Ärzte - Elke](https://www.youtube.com/watch?v=nPh8ynbm0Q8) **Dänemark - Jaime Lannister **6 points
            9. [Kaiser Chiefs - Ruby](https://www.youtube.com/watch?v=qObzgUfCl28) **Bahamas - EdgeGF **7 points
            8. [Tom Petty And The Heartbreakers - Mary Jane's Last Dance](https://www.youtube.com/watch?v=aowSGxim_O8) **Jamaika - Julian **8 points
            7. [Mika - Grace Kelly](https://www.youtube.com/watch?v=0CGVgAYJyjk) **Uganda - Berggorilla **9 points
            6. [Stick To Your Guns - Amber](https://www.youtube.com/watch?v=WrjibuzikUU) **Norwegen - Mark Webber **10 points
            5. [Weezer - Buddy Holly](https://www.youtube.com/watch?v=Kjr7US2Z9aY) **Portugal - Red Forman **11 points

            4. [Casper - Michael X](https://www.youtube.com/watch?v=ekzx1T9-gPI) **Japan - Grissom **13 points

            3. [The Police - Roxanne](https://www.youtube.com/watch?v=3T1c7GkzRQQ) **Saarland - Peter Neururer **16 points

            2. [Lady Gaga - Alejandro](https://youtu.be/niqrrmev4mA) **Puerto Rico - Jay Halstead **20 points

            1. [Blondie - Maria](https://www.youtube.com/watch?v=IwodQdM4hvk) **Irland - Kingtoo **25 points
            """;
}
