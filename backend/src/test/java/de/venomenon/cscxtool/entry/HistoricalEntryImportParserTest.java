package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryImportParserTest {

    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(new CountryCatalog(new ObjectMapper()));

    @Test
    void parsesTheBindingPublishedHistoricalFixtureWithoutInventingLinksOrCorrectingSourceText() {
        List<HistoricalImportPreviewLine> lines = parser.parse(FIXTURE, "", participants());

        assertThat(lines).hasSize(27);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.artist()).isNotBlank();
            assertThat(line.title()).isNotBlank();
            assertThat(line.participantId()).isNotNull();
            assertThat(line.youtubeUrl()).isNull();
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
        assertThat(lines.get(8)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Penatonix feat. Ateez");
            assertThat(line.title()).isEqualTo("A Little Space");
        });
        assertThat(lines.get(17)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Foo Fighters");
            assertThat(line.participantDisplayName()).isEqualTo("snaggletooth");
            assertThat(line.countryToken()).isEqualTo("Schottland");
        });
        assertThat(lines.get(19).participantDisplayName()).isEqualTo("Kingtoo");
        assertThat(lines.get(25).participantDisplayName()).isEqualTo("-Frollo-");
    }

    @Test
    void keepsCountryConflictsVisibleWhileTheKnownParticipantRemainsAuthoritative() {
        List<HistoricalImportPreviewLine> lines = parser.parse(
                "", "Imminence - Paralyzed (Deutschland/Cortez)", participants()
        );

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Cortez");
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("COUNTRY_CONFLICT");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.WARNING);
        });
    }

    @Test
    void parsesTheSameFixtureFromRichClipboardBlocksAndResolvesAliases() {
        String html = "<div>" + String.join("", FIXTURE.lines().filter(line -> !line.isBlank())
                .map(line -> "<p>" + line + "</p>").toList()) + "</div>";
        List<HistoricalImportPreviewLine> richLines = parser.parse(html, "", participants());
        List<HistoricalImportPreviewLine> aliasedLine = parser.parse(
                "", "Imminence - Paralyzed (Finnland/Cortez-Alt)", participants()
        );

        assertThat(richLines).hasSize(27);
        assertThat(richLines.get(17).participantDisplayName()).isEqualTo("snaggletooth");
        assertThat(aliasedLine).singleElement().satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Cortez");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
    }

    @Test
    void deduplicatesHtmlAndPlaintextRepresentationsFromOnePasteAndKeepsTheRichLink() {
        String source = "Imminence - Paralyzed (Finnland/Cortez)";
        String html = "<p><a href=\"https://source.example/imminence\">Imminence - Paralyzed</a> (Finnland/Cortez)</p>";

        List<HistoricalImportPreviewLine> lines = parser.parse(html, source, participants());

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.sourceText()).isEqualTo(source);
            assertThat(line.youtubeUrl()).isEqualTo("https://source.example/imminence");
            assertThat(line.participantDisplayName()).isEqualTo("Cortez");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
    }

    @Test
    void parsesEveryBindingAnnouncementFormatLineWithoutChangingTheSourceValues() {
        List<HistoricalImportPreviewLine> lines = parser.parse("", FORMAT_B, participants());

        assertThat(lines).hasSize(28);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).isNull();
            assertThat(line.participantId()).isNotNull();
        });
        assertThat(lines).extracting(line -> line.artist() + " - " + line.title()).containsExactly(
                "IVE - I AM", "Snoop Dogg & Wiz Khalifa feat. Bruno Mars - Young, Wild & Free",
                "KONGOS - Come with Me Now", "P!nk - Get The Party Started", "Eddie Murphy - Party All The Time",
                "Erik Cohen - Club Pinasse", "Adam Green - Emily", "Maitre Gims - Est-ce que tu m'aimes?",
                "Electic Callboy - Everytime We Touch", "Elton John - I'm Still Standing",
                "Sunstroke Project & Olia Tira - Run Away", "Miley Cyrus - Flowers",
                "Earth, Wind & Fire - Boogie Wonderland", "Linkin Park - Somewhere I Belong",
                "Farin Urlaub Racing Team - Am Strand", "R.I.O feat. U-Jean - Summer Jam",
                "Goldfinger - Superman", "Mando Diao - Long Before Rock 'n' Roll", "Nik Kershaw - The Riddle",
                "Red Hot Chilli Peppers - One Way Traffic", "The Weeknd - Blinding Lights", "Turnstile - Holiday",
                "Billie Eilish - Everything I Wanted", "S Club - Bring It All Back",
                "Roger Whittaker - Ein bissichen Aroma", "Tarrus Riley - My Day", "Millencolin - Da Strike",
                "Gloria Gaynor - I Will Survive"
        );
        assertThat(lines.get(6).participantDisplayName()).isEqualTo("Daniel.");
        assertThat(lines.get(8).artist()).isEqualTo("Electic Callboy");
        assertThat(lines.get(19).participantDisplayName()).isEqualTo("The Red-NGA Shankmos");
        assertThat(lines.get(13).countryToken()).isEqualTo("Luxemburg");
        assertThat(lines.get(14).countryToken()).isEqualTo("Samoa");
    }

    @Test
    void parsesTheFullBindingLinkedFormatFromStructuralHtmlAndPrefersItOverEquivalentMarkdown() {
        List<HistoricalImportPreviewLine> lines = parser.parse(asRichHtml(FORMAT_C), FORMAT_C, participants());
        List<HistoricalImportPreviewLine> markdownFallback = parser.parse("", FORMAT_C, participants());

        assertThat(lines).hasSize(28);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
            assertThat(line.youtubeUrl()).startsWith("https://");
            assertThat(line.participantId()).isNotNull();
        });
        assertThat(lines).extracting(line -> line.artist() + " - " + line.title()).containsExactlyElementsOf(formatCValues(FORMAT_C, 2));
        assertThat(lines).extracting(HistoricalImportPreviewLine::youtubeUrl).containsExactlyElementsOf(formatCValues(FORMAT_C, 3));
        assertThat(markdownFallback).hasSize(28);
        assertThat(markdownFallback).allSatisfy(line -> assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY));
        assertThat(markdownFallback).extracting(HistoricalImportPreviewLine::youtubeUrl).containsExactlyElementsOf(formatCValues(FORMAT_C, 3));
        assertThat(lines.get(0)).satisfies(line -> {
            assertThat(line.artist()).isEqualTo("The Weeknd");
            assertThat(line.title()).isEqualTo("Blinding Lights");
            assertThat(line.participantDisplayName()).isEqualTo("Mark Webber");
            assertThat(line.youtubeUrl()).isEqualTo("https://youtu.be/BkaNfAvPsyQ");
        });
        assertThat(lines.get(1).participantDisplayName()).isEqualTo("Scott D'Amore");
        assertThat(lines.get(4).participantDisplayName()).isEqualTo("KlötenKlaus");
        assertThat(lines.get(18).participantDisplayName()).isEqualTo("Daniel.");
        assertThat(lines.get(19).participantDisplayName()).isEqualTo("The Red-NGA Shankmos");
        assertThat(lines.get(22)).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Clementine Lyon");
            assertThat(line.artist()).isEqualTo("KONGOS");
            assertThat(line.youtubeUrl()).isEqualTo("https://www.youtube.com/watch?v=Gz2GVlQkn4Q");
        });
        assertThat(lines.get(27)).satisfies(line -> {
            assertThat(line.participantDisplayName()).isEqualTo("Die Ente");
            assertThat(line.countryToken()).isEqualTo("Vatikanstadt");
        });
    }

    @Test
    void keepsAccentedParticipantNamesUnresolvedUnlessTheyAreAConfiguredAlias() {
        List<HistoricalImportParticipant> withoutAccentedAlias = participants().stream().map(participant ->
                participant.participantId() == 6
                        ? participant(6, "Clementine Lyon", "CH")
                        : participant
        ).toList();

        List<HistoricalImportPreviewLine> lines = parser.parse("", FORMAT_C, withoutAccentedAlias);

        assertThat(lines.get(22)).satisfies(line -> {
            assertThat(line.participantId()).isNull();
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("UNRESOLVED_PARTICIPANT");
        });
    }

    @Test
    void keepsConflictingHtmlAndPlaintextRepresentationsVisibleInsteadOfOverwritingEitherOne() {
        String html = "<p><strong>Australien&nbsp;-&nbsp;Mark Webber </strong><a href=\"https://youtu.be/BkaNfAvPsyQ\">The Weeknd - Blinding Lights</a></p>";
        String text = "**Australien - Mark Webber **[The Weeknd - Different Song](https://youtu.be/other)";

        List<HistoricalImportPreviewLine> lines = parser.parse(html, text, participants());

        assertThat(lines).hasSize(2);
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.WARNING);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("REPRESENTATION_CONFLICT");
        });
    }

    @Test
    void leavesAmbiguousLinksAndUnknownMixedFormatLinesVisibleForManualCorrection() {
        String multipleLinks = "<p><strong>Australien - Mark Webber </strong><a href=\"https://youtu.be/BkaNfAvPsyQ\">The Weeknd - Blinding Lights</a><a href=\"https://example.test/other\">weitere Quelle</a></p>";
        List<HistoricalImportPreviewLine> ambiguous = parser.parse(multipleLinks, "", participants());
        List<HistoricalImportPreviewLine> mixed = parser.parse("", """
                Imminence - Paralyzed (Finnland/Cortez)
                IVE - I AM - Die Ente / Vatikanstaat
                **Australien - Mark Webber **[The Weeknd - Blinding Lights](https://youtu.be/BkaNfAvPsyQ)
                Broken - source
                """, participants());

        assertThat(ambiguous).singleElement().satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("AMBIGUOUS_LINKS");
        });
        assertThat(mixed).hasSize(4);
        assertThat(mixed.get(3)).satisfies(line -> {
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.INCOMPLETE);
            assertThat(line.warnings()).extracting(ImportWarning::code).contains("UNRECOGNIZED_FORMAT");
        });
    }

    @Test
    void keepsAdditionalHyphensAndSlashesInsideFormatBSongText() {
        List<HistoricalImportPreviewLine> lines = parser.parse(
                "", "Band - Titel - live / edit - The Red-NGA Shankmos /Nigeria", participants()
        );

        assertThat(lines).singleElement().satisfies(line -> {
            assertThat(line.artist()).isEqualTo("Band");
            assertThat(line.title()).isEqualTo("Titel - live / edit");
            assertThat(line.participantDisplayName()).isEqualTo("The Red-NGA Shankmos");
            assertThat(line.status()).isEqualTo(ImportPreviewStatus.READY);
        });
    }

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Cortez", "FI", "Cortez-Alt"), participant(2, "The Red-NGA Shankmos", "NG"), participant(3, "Jamie Hayter", "NZ"),
                participant(4, "Rated M", "BR"), participant(5, "Toblerone Driver", "TR"), participant(6, "Clementine Lyon", "CH", "Clémentine Lyon"),
                participant(7, "Ratcatcher 2", "PT"), participant(8, "Serhou Guirassy", "JM"), participant(9, "Die Ente", "VA"),
                participant(10, "Dr. King Schultz", "KR"), participant(11, "Fletcher Cox", "NR"), participant(12, "OMW", "WS"),
                participant(13, "Ravenous", "GU"), participant(14, "Daniel.", "NL"), participant(15, "KlötenKlaus", "CR"),
                participant(16, "Kenny Ospreay", "LU"), participant(17, "McKlariato", "CN"), participant(18, "snaggletooth", "XS"),
                participant(19, "Scott D'Amore", "BA"), participant(20, "Kingtoo", "MN"), participant(21, "Mark Webber", "AU"),
                participant(22, "Worm", "DE"), participant(23, "Grissom", "JP"), participant(24, "Contiomagus", "ZA"),
                participant(25, "Everton", "GR"), participant(26, "-Frollo-", "MT"), participant(27, "Berggorilla", "UG"),
                participant(28, "Roman Reigns", "MT"), participant(29, "Straßenköter", "MX"), participant(30, "PrettyFlamingo", "CG")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return participant(id, name, countryCode, new String[0]);
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode, String... aliases) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of(aliases));
    }

    private static final String FIXTURE = """
            Imminence - Paralyzed (Finnland/Cortez)

            The Killers - Read My Mind (Nigeria/The Red-NGA Shankmos)

            Alice In Chains - Would? (Neuseeland/Jamie Hayter)

            Nirvana - About A Girl (Brasilien/Rated M)

            Christina Stürmer - Ich lebe (Türkei/Toblerone Driver)

            Guns 'n' Roses - Patience (Schweiz/Clementine Lyon)

            30 Seconds To Mars - Hurricane (Portugal/Ratcatcher 2)

            Common Kings - No Other Love (Jamaica/Serhou Guirassy)

            Penatonix feat. Ateez - A Little Space (Vatikan/Die Ente)

            John Denver - Take Me Home, Country Roads (Südkorea/Dr. King Schultz)

            Glass Vase Cello Case - Tattle Tale (Nauru/Fletcher Cox)

            Bodo Wartke - Ja, Schatz! (Samoa/OMW)

            Frank Turner - Bat out of Hell (Guam/Ravenous)

            Scott Bradlee’s Postmodern Jukebox feat Annie Bosko - Complicated (Niederlande/Daniel.)

            Sam Ryder - Tiny Riot (Costa Rica/KlötenKlaus)

            Pur - D-Mark (Luxemburg/Kenny Ospreay)

            KEiiNO - Monument (China/McKlariato)

            Foo Fighters - Times Like These (snaggletooth/Schottland)

            Pearl Jam - Black (Bosnien und Herzegowina/Scott D'Amore)

            Nightwish - Islander (Mongolei/Kingtoo)

            Stick To Your Guns - Nobody (Australien/Mark Webber)

            The Mayries - The Middle (Deutschland/Worm)

            Kim Boyko - The Sciences (Japan/Grissom)

            Eric Clapton - Layla (Südafrika/Contiomagus)

            Dido - Thank You (Griechenland/Everton)

            Linkin Park - Crawling (Malta/-Frollo-)

            Justin Bieber - Fast Car (Uganda/Berggorilla)
            """;

    private static String asRichHtml(String markdown) {
        return markdown.lines().filter(line -> !line.isBlank()).map(HistoricalEntryImportParserTest::asRichHtmlLine)
                .collect(Collectors.joining());
    }

    private static String asRichHtmlLine(String markdownLine) {
        Matcher match = FORMAT_C_LINE.matcher(markdownLine);
        if (!match.matches()) throw new IllegalArgumentException("Ungültige Format-C-Fixturezeile");
        return "<p><strong>" + match.group(1) + "&nbsp;</strong><a href=\"" + match.group(3) + "\">"
                + match.group(2) + "</a></p>";
    }

    private static List<String> formatCValues(String markdown, int group) {
        return markdown.lines().filter(line -> !line.isBlank()).map(line -> {
            Matcher match = FORMAT_C_LINE.matcher(line);
            if (!match.matches()) throw new IllegalArgumentException("Ungültige Format-C-Fixturezeile");
            return match.group(group);
        }).toList();
    }

    private static final Pattern FORMAT_C_LINE = Pattern.compile("^\\*\\*(.*?) \\*\\*\\[(.+)]\\((https?://.+)\\)$");

    private static final String FORMAT_B = """
            IVE - I AM - Die Ente / Vatikanstaat
            Snoop Dogg & Wiz Khalifa feat. Bruno Mars - Young, Wild & Free - Roman Reigns / Malta
            KONGOS - Come with Me Now - Clementine Lyon / Schweiz
            P!nk - Get The Party Started - Scott D'Amore / Bosnien und Herzegowina
            Eddie Murphy - Party All The Time - Berggorilla / Uganda
            Erik Cohen - Club Pinasse - Ravenous / Guam
            Adam Green - Emily - Daniel. / Niederlande
            Maitre Gims - Est-ce que tu m'aimes? - Kingtoo / Mongolei
            Electic Callboy - Everytime We Touch - KlötenKlaus / Costa Rica
            Elton John - I'm Still Standing - Everton / Griechenland
            Sunstroke Project & Olia Tira - Run Away - McKlariato / China
            Miley Cyrus - Flowers - Straßenköter / Mexiko
            Earth, Wind & Fire - Boogie Wonderland - Rated M / Brasilien
            Linkin Park - Somewhere I Belong - Kenny Ospreay /Luxemburg
            Farin Urlaub Racing Team - Am Strand - OMW /Samoa
            R.I.O feat. U-Jean - Summer Jam - Worm / Deutschland
            Goldfinger - Superman - Dr. King Schultz / Südkorea
            Mando Diao - Long Before Rock 'n' Roll - Grissom / Japan
            Nik Kershaw - The Riddle - Fletcher Cox / Nauru
            Red Hot Chilli Peppers - One Way Traffic - The Red-NGA Shankmos /Nigeria
            The Weeknd - Blinding Lights - Mark Webber / Australien
            Turnstile - Holiday - Cortez / Finnland
            Billie Eilish - Everything I Wanted - Toblerone Driver / Türkei
            S Club - Bring It All Back - Jamie Hayter / Neuseeland
            Roger Whittaker - Ein bissichen Aroma - Contiomagus / Südafrika
            Tarrus Riley - My Day - Serhou Guirassy / Jamaica
            Millencolin - Da Strike - PrettyFlamingo / Kongo
            Gloria Gaynor - I Will Survive - snaggletooth / Schottland
            """;

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
