package de.venomenon.cscxtool.publishedballot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PublishedBallotApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PublishedBallotImportParser parser;
    @Autowired private PublishedBallotRepository ballotRepository;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void importsTheMandatoryFrolloFixtureAtomicallyAndKeepsDerivedStatesDistinct() throws Exception {
        Fixture fixture = fixture();

        HttpResponse<String> preview = post("/api/shows/" + fixture.showId + "/published-ballots/import-preview",
                "{\"html\":\"\",\"text\":\"" + json(FROLLO_FIXTURE) + "\"}");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"rank\":15", "Eric Clapton", "Layla", "\"rank\":1", "John Denver", "Take Me Home, Country Roads");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot", Integer.class)).isZero();

        String validImport = ballotImport(fixture.voterParticipationId, fixture.rankedEntryIds, false);
        assertThat(post("/api/shows/" + fixture.showId + "/published-ballots/import", validImport).statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot_position", Integer.class)).isEqualTo(15);
        assertThat(get("/api/data/export/full").body()).contains("\"formatVersion\":9", "\"publishedBallots\"", "\"publishedBallotPositions\"", "\"status\":\"ABGESTIMMT\"");
        assertThat(get("/api/data/export/published-ballots.csv").body()).contains("Stimmzettelstatus", "RANKED", "ABGESTIMMT", "Eric Clapton");

        HttpResponse<String> detail = get("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.voterParticipationId);
        assertThat(detail.body()).contains("\"status\":\"ABGESTIMMT\"", "\"state\":\"RANKED\"", "\"state\":\"OUTSIDE_TOP_15\"", "\"state\":\"OWN_ENTRY\"", "\"points\":25");

        assertThat(post("/api/shows/" + fixture.showId + "/published-ballots/import",
                ballotImport(fixture.voterParticipationId, fixture.rankedEntryIds.subList(0, 14), true)).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot_position", Integer.class)).isEqualTo(15);

        List<Long> ownEntry = new ArrayList<>(fixture.rankedEntryIds);
        ownEntry.set(0, fixture.ownEntryId);
        HttpResponse<String> ownImport = post("/api/shows/" + fixture.showId + "/published-ballots/import",
                ballotImport(fixture.voterParticipationId, ownEntry, true));
        assertThat(ownImport.statusCode()).isEqualTo(409);
        assertThat(ownImport.body()).contains("OWN_ENTRY_IN_BALLOT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot_position", Integer.class)).isEqualTo(15);

        assertThat(post("/api/shows/" + fixture.showId + "/entries/entry-list/reopen", "").statusCode()).isEqualTo(409);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM contest_entry WHERE id = ?", fixture.rankedEntryIds.getFirst()))
                .hasMessageContaining("FOREIGN KEY");

        assertThat(put("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.voterParticipationId + "/status",
                "{\"status\":\"NICHT_ABGESTIMMT\"}").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot_position", Integer.class)).isZero();
        assertThat(get("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.voterParticipationId).body()).contains("\"state\":\"NO_BALLOT\"");
        assertThat(put("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.voterParticipationId + "/status",
                "{\"status\":\"UNERFASST\"}").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot", Integer.class)).isZero();
        assertThat(get("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.voterParticipationId).body()).contains("\"status\":\"UNERFASST\"", "\"state\":\"UNKNOWN\"");
    }

    @Test
    void parsesTheFrolloFixtureFromMarkdownPlainTextHtmlAndConsecutiveBlocks() throws Exception {
        Fixture fixture = fixture();
        String markdown = FROLLO_FIXTURE.replaceFirst("\\[\\#3] Malta - -Frollo-", "**[#3] Malta \u2013 -Frollo-**")
                .replaceFirst("1 punt   ", "**1 punt\u00a0\u00a0\u00a0").replaceFirst("Layla", "Layla**");
        List<PublishedBallotPreviewBlock> plain = parser.parse("", markdown,
                ballotRepository.findParticipants(fixture.showId), ballotRepository.findEntries(fixture.showId), java.util.Set.of());
        assertThat(plain).hasSize(1);
        assertThat(plain.getFirst().participationId()).isEqualTo(fixture.voterParticipationId);
        assertThat(plain.getFirst().positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(plain.getFirst().positions().getFirst().entryId()).isEqualTo(fixture.rankedEntryIds.getFirst());
        assertThat(plain.getFirst().positions().getLast().entryId()).isEqualTo(fixture.rankedEntryIds.getLast());

        String html = FROLLO_FIXTURE.lines().map(line -> "<p>" + line + "</p>")
                .collect(java.util.stream.Collectors.joining());
        assertThat(parser.parse(html, "untrusted plain-text fallback",
                ballotRepository.findParticipants(fixture.showId), ballotRepository.findEntries(fixture.showId), java.util.Set.of()))
                .singleElement().satisfies(block -> assertThat(block.positions()).hasSize(15));
        assertThat(parser.parse("", FROLLO_FIXTURE + "\n\n" + FROLLO_FIXTURE,
                ballotRepository.findParticipants(fixture.showId), ballotRepository.findEntries(fixture.showId), java.util.Set.of()))
                .hasSize(2);
    }

    private Fixture fixture() throws Exception {
        long contestId = id(post("/api/contests", "{\"name\":\"CSC IX Test " + FIXTURE_SEQUENCE.incrementAndGet() + "\"}").body(), "id");
        Participant voter = participant(contestId, "-Frollo-", "MT");
        List<Song> rankedSongs = List.of(
                new Song("Eric Clapton", "Layla", "Contiomagus", "ZA"),
                new Song("Nirvana", "About A Girl", "Submit2", "DE"),
                new Song("Christina Stürmer", "Ich lebe", "Submit3", "TR"),
                new Song("Guns 'n' Roses", "Patience", "Submit4", "CH"),
                new Song("30 Seconds To Mars", "Hurricane", "Submit5", "PT"),
                new Song("Common Kings", "No Other Love", "Submit6", "JM"),
                new Song("Penatonix feat. Ateez", "A Little Space", "Submit7", "VA"),
                new Song("Glass Vase Cello Case", "Tattle Tale", "Submit8", "NR"),
                new Song("Bodo Wartke", "Ja, Schatz!", "Submit9", "WS"),
                new Song("Frank Turner", "Bat out of Hell", "Submit10", "GU"),
                new Song("Scott Bradlee’s Postmodern Jukebox feat Annie Bosko", "Complicated", "Submit11", "NL"),
                new Song("Sam Ryder", "Tiny Riot", "Submit12", "CR"),
                new Song("Pur", "D-Mark", "Submit13", "LU"),
                new Song("Foo Fighters", "Times Like These", "Submit14", "GB"),
                new Song("John Denver", "Take Me Home, Country Roads", "Dr. King Schultz", "KR")
        );
        List<Participant> submitters = new ArrayList<>();
        for (Song song : rankedSongs) submitters.add(participant(contestId, song.submitter(), song.countryCode()));
        Participant outside = participant(contestId, "OutsideUser", "FI");
        long showId = id(post("/api/contests/" + contestId + "/shows", "{\"showNumber\":3,\"name\":\"Archivthema\"}").body(), "id");

        StringBuilder entries = new StringBuilder("{\"entries\":[");
        for (int index = 0; index < rankedSongs.size(); index++) {
            if (index > 0) entries.append(',');
            Song song = rankedSongs.get(index);
            entries.append("{\"artist\":\"").append(json(song.artist())).append("\",\"title\":\"").append(json(song.title()))
                    .append("\",\"youtubeUrl\":null,\"participantId\":").append(submitters.get(index).participantId()).append('}');
        }
        entries.append(",{\"artist\":\"Own Artist\",\"title\":\"Own Song\",\"youtubeUrl\":null,\"participantId\":").append(voter.participantId()).append('}');
        entries.append(",{\"artist\":\"Outside Artist\",\"title\":\"Outside Song\",\"youtubeUrl\":null,\"participantId\":").append(outside.participantId()).append("}]}");
        assertThat(post("/api/shows/" + showId + "/entries/historical-import", entries.toString()).statusCode()).isEqualTo(200);
        assertThat(post("/api/shows/" + showId + "/entries/entry-list/complete", "").statusCode()).isEqualTo(204);
        List<Long> entryIds = rankedSongs.stream().map(song -> jdbc.queryForObject(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = ? AND title = ?", Long.class, showId, song.artist(), song.title()
        )).toList();
        long ownEntryId = jdbc.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = 'Own Artist'", Long.class, showId);
        return new Fixture(showId, voter.participationId(), entryIds, ownEntryId);
    }

    private Participant participant(long contestId, String displayName, String countryCode) throws Exception {
        HttpResponse<String> response = post("/api/contests/" + contestId + "/participants", "{\"displayName\":\"" + json(displayName)
                + "\",\"countryCode\":\"" + countryCode + "\",\"active\":true}");
        assertThat(response.statusCode()).isEqualTo(201);
        long participationId = id(response.body(), "participationId");
        long participantId = jdbc.queryForObject("SELECT participant_id FROM contest_participation WHERE id = ?", Long.class, participationId);
        return new Participant(participationId, participantId);
    }

    private static String ballotImport(long participationId, List<Long> entryIds, boolean replaceExisting) {
        StringBuilder json = new StringBuilder("{\"ballots\":[{\"participationId\":").append(participationId)
                .append(",\"replaceExisting\":").append(replaceExisting).append(",\"positions\":[");
        for (int index = 0; index < entryIds.size(); index++) {
            if (index > 0) json.append(',');
            json.append("{\"entryId\":").append(entryIds.get(index)).append(",\"rank\":").append(15 - index).append('}');
        }
        return json.append("]}]} ").toString();
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
    private HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body); }
    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static long id(String body, String property) {
        int start = body.indexOf("\"" + property + "\":") + property.length() + 3;
        int end = body.indexOf(',', start);
        if (end < 0) end = body.indexOf('}', start);
        return Long.parseLong(body.substring(start, end));
    }
    private static String json(String text) { return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-published-ballot-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception); }
    }

    private record Participant(long participationId, long participantId) { }
    private record Song(String artist, String title, String submitter, String countryCode) { }
    private record Fixture(long showId, long voterParticipationId, List<Long> rankedEntryIds, long ownEntryId) { }

    // The documented Frollo source keeps its real edge cases: Malta/-Frollo-, `punt`/`punti`, Unicode and
    // punctuation. Source order alone establishes the ranks; neither the displayed numbers nor the points word do.
    private static final String FROLLO_FIXTURE = """
            [#3] Malta - -Frollo-

            1 punt   Südafrika - Contiomagus   Eric Clapton - Layla
            2 punti   Deutschland - Submit2   Nirvana - About A Girl
            3 points   Türkei - Submit3   Christina Stürmer - Ich lebe
            4 punt   Schweiz - Submit4   Guns 'n' Roses - Patience
            5 punti   Portugal - Submit5   30 Seconds To Mars - Hurricane
            6 points   Jamaica - Submit6   Common Kings - No Other Love
            7 punt   Vatikan - Submit7   Penatonix feat. Ateez - A Little Space
            8 punti   Nauru - Submit8   Glass Vase Cello Case - Tattle Tale
            9 points   Samoa - Submit9   Bodo Wartke - Ja, Schatz!
            10 punt   Guam - Submit10   Frank Turner - Bat out of Hell
            11 punti   Niederlande - Submit11   Scott Bradlee’s Postmodern Jukebox feat Annie Bosko - Complicated
            12 points   Costa Rica - Submit12   Sam Ryder - Tiny Riot
            13 punt   Luxemburg - Submit13   Pur - D-Mark
            16 punti   Vereinigtes Königreich - Submit14   Foo Fighters - Times Like These
            25 points   Südkorea - Dr. King Schultz   John Denver - Take Me Home, Country Roads
            """;
}
