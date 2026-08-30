package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class HistoricalEntryImportParserTest {

    private final HistoricalEntryImportParser parser = new HistoricalEntryImportParser(
            new YoutubeUrlNormalizer(), new CountryCatalog(new ObjectMapper())
    );

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

    private static List<HistoricalImportParticipant> participants() {
        return List.of(
                participant(1, "Cortez", "FI"), participant(2, "The Red-NGA Shankmos", "NG"), participant(3, "Jamie Hayter", "NZ"),
                participant(4, "Rated M", "BR"), participant(5, "Toblerone Driver", "TR"), participant(6, "Clementine Lyon", "CH"),
                participant(7, "Ratcatcher 2", "PT"), participant(8, "Serhou Guirassy", "JM"), participant(9, "Die Ente", "VA"),
                participant(10, "Dr. King Schultz", "KR"), participant(11, "Fletcher Cox", "NR"), participant(12, "OMW", "WS"),
                participant(13, "Ravenous", "GU"), participant(14, "Daniel.", "NL"), participant(15, "KlötenKlaus", "CR"),
                participant(16, "Kenny Ospreay", "LU"), participant(17, "McKlariato", "CN"), participant(18, "snaggletooth", "XS"),
                participant(19, "Scott D'Amore", "BA"), participant(20, "Kingtoo", "MN"), participant(21, "Mark Webber", "AU"),
                participant(22, "Worm", "DE"), participant(23, "Grissom", "JP"), participant(24, "Contiomagus", "ZA"),
                participant(25, "Everton", "GR"), participant(26, "-Frollo-", "MT"), participant(27, "Berggorilla", "UG")
        );
    }

    private static HistoricalImportParticipant participant(long id, String name, String countryCode) {
        return new HistoricalImportParticipant(id, id, name, countryCode, List.of());
    }

    private static final String FIXTURE = """
            Imminence - Paralyzed (Finnland/Cortez)

            The Killers - Read My Mind (Nigeria/The Red-NGA Shankmos)

            Alice In Chains - Would? (Neuseeland/Jamie Hayter)

            Nirvana - About A Girl (Brasilien/Rated M)

            Christina Stürmer - Ich lebe (Türkei/Toblerone Driver)

            Guns 'n' Roses - Patience (Schweiz/Clementine Lyon)

            30 Seconds To Mars - Hurricane (Portugal/Ratcatcher 2)

            Common Kings - No Other Love (Jamaika/Serhou Guirassy)

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
}
