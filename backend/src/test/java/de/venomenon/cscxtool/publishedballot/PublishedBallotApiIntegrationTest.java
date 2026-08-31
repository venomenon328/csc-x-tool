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
    void derivesShowStandingsOnlyFromCurrentVotedBallotsWithoutTieBreaksOrPersistence() throws Exception {
        Fixture fixture = fixture();
        List<Long> firstBallot = new ArrayList<>();
        firstBallot.add(fixture.outsideEntryId());
        firstBallot.addAll(fixture.rankedEntryIds().subList(2, 15));
        firstBallot.add(fixture.rankedEntryIds().getFirst());
        List<Long> secondBallot = new ArrayList<>();
        secondBallot.add(fixture.ownEntryId());
        secondBallot.addAll(fixture.rankedEntryIds().subList(2, 15));
        secondBallot.add(fixture.rankedEntryIds().get(1));

        assertThat(post("/api/shows/" + fixture.showId + "/published-ballots/import",
                ballotImport(fixture.voterParticipationId, firstBallot, false)).statusCode()).isEqualTo(200);
        assertThat(post("/api/shows/" + fixture.showId + "/published-ballots/import",
                ballotImport(fixture.secondVoterParticipationId(), secondBallot, false)).statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.notVoterParticipationId() + "/status",
                "{\"status\":\"NICHT_ABGESTIMMT\"}").statusCode()).isEqualTo(204);

        HttpResponse<String> initial = get("/api/shows/" + fixture.showId + "/published-ballots/standings");
        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.body()).contains("\"votedCount\":2", "\"notVotedCount\":1", "\"unrecordedCount\":16");
        assertStanding(initial.body(), fixture.rankedEntryIds().getLast(), 1, 40, 2);
        assertStanding(initial.body(), fixture.rankedEntryIds().getFirst(), 4, 25, 1);
        assertStanding(initial.body(), fixture.rankedEntryIds().get(1), 4, 25, 1);
        assertStanding(initial.body(), fixture.ownEntryId(), 16, 1, 1);
        assertStanding(initial.body(), fixture.zeroEntryId(), 18, 0, 0);

        List<Long> replacement = new ArrayList<>(fixture.rankedEntryIds());
        assertThat(post("/api/shows/" + fixture.showId + "/published-ballots/import",
                ballotImport(fixture.secondVoterParticipationId(), replacement, true)).statusCode()).isEqualTo(200);
        HttpResponse<String> replaced = get("/api/shows/" + fixture.showId + "/published-ballots/standings");
        assertStanding(replaced.body(), fixture.rankedEntryIds().get(1), 15, 2, 1);

        assertThat(put("/api/shows/" + fixture.showId + "/published-ballots/" + fixture.secondVoterParticipationId() + "/status",
                "{\"status\":\"UNERFASST\"}").statusCode()).isEqualTo(204);
        HttpResponse<String> reset = get("/api/shows/" + fixture.showId + "/published-ballots/standings");
        assertThat(reset.body()).contains("\"votedCount\":1", "\"notVotedCount\":1", "\"unrecordedCount\":17");
        assertStanding(reset.body(), fixture.ownEntryId(), 16, 0, 0);

        Fixture otherShow = fixture();
        assertThat(post("/api/shows/" + otherShow.showId + "/published-ballots/import",
                ballotImport(otherShow.voterParticipationId, otherShow.rankedEntryIds, false)).statusCode()).isEqualTo(200);
        HttpResponse<String> isolated = get("/api/shows/" + fixture.showId + "/published-ballots/standings");
        assertThat(isolated.body()).contains("\"mottoShowId\":" + fixture.showId, "\"votedCount\":1");
        assertStanding(isolated.body(), fixture.rankedEntryIds().getLast(), 1, 20, 1);
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

    @Test
    void parsesTheJapanGrissomFixtureWithAttachedOpaquePointsPrefixesInPlainMarkdownAndRichHtml() {
        List<PublishedBallotParticipant> participants = japanParticipants();
        List<PublishedBallotEntry> entries = japanEntries();

        PublishedBallotPreviewBlock plain = parser.parse("", JAPAN_FIXTURE, participants, entries, java.util.Set.of()).getFirst();
        assertThat(plain.participationId()).isEqualTo(1);
        assertThat(plain.displayName()).isEqualTo("Grissom");
        assertThat(plain.countryCode()).isEqualTo("JP");
        assertThat(plain.positions()).extracting(PublishedBallotPreviewPosition::rank)
                .containsExactly(15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
        assertThat(plain.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
        assertThat(plain.positions().getFirst()).extracting(PublishedBallotPreviewPosition::artist, PublishedBallotPreviewPosition::title)
                .containsExactly("Nik Kershaw", "The Riddle");
        assertThat(plain.positions().getLast()).extracting(PublishedBallotPreviewPosition::artist, PublishedBallotPreviewPosition::title)
                .containsExactly("Adam Green", "Emily");

        PublishedBallotPreviewBlock markdown = parser.parse("", japanMarkdown(), participants, entries, java.util.Set.of()).getFirst();
        assertThat(markdown.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);

        PublishedBallotPreviewBlock rich = parser.parse(japanRichHtml(), JAPAN_FIXTURE, participants, entries, java.util.Set.of()).getFirst();
        assertThat(rich.positions()).extracting(PublishedBallotPreviewPosition::entryId)
                .containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
    }

    @Test
    void treatsPointsWordsAndScriptsAsOpaquePrefixDecoration() {
        List<PublishedBallotParticipant> participants = japanParticipants();
        PublishedBallotEntry entry = japanEntries().getFirst();
        for (String prefix : List.of("1 punkt", "1punkt", "1点", "1ポイント", "1نقطة", "**1**punkt**", "1**p**unkt", "**1点****")) {
            PublishedBallotPreviewBlock preview = parser.parse(
                    "", "[#1] Japan - Grissom\n" + prefix + " Nauru - Fletcher Cox Nik Kershaw - The Riddle",
                    participants, List.of(entry), java.util.Set.of()
            ).getFirst();
            assertThat(preview.positions()).singleElement().extracting(PublishedBallotPreviewPosition::entryId).isEqualTo(1L);
        }
    }

    @Test
    void retainsPositionCountForActualCountsAndExplainsUnrecognizedRatingLines() {
        List<PublishedBallotParticipant> participants = japanParticipants();
        List<PublishedBallotEntry> entries = japanEntries();

        PublishedBallotPreviewBlock fourteen = parser.parse(
                "", japanFixtureWithPositionCount(14), participants, entries, java.util.Set.of()
        ).getFirst();
        assertThat(fourteen.positions()).hasSize(14);
        assertThat(fourteen.warnings()).extracting(BallotImportWarning::code)
                .contains("POSITION_COUNT").doesNotContain("UNRECOGNIZED_POSITION_LINES");

        PublishedBallotPreviewBlock sixteen = parser.parse(
                "", japanFixtureWithPositionCount(16), participants, entries, java.util.Set.of()
        ).getFirst();
        assertThat(sixteen.positions()).hasSize(16);
        assertThat(sixteen.warnings()).extracting(BallotImportWarning::code)
                .contains("POSITION_COUNT").doesNotContain("UNRECOGNIZED_POSITION_LINES");

        PublishedBallotPreviewBlock unreadable = parser.parse(
                "", "[#1] Japan - Grissom\n" + "1\n".repeat(15), participants, entries, java.util.Set.of()
        ).getFirst();
        assertThat(unreadable.positions()).isEmpty();
        assertThat(unreadable.warnings()).extracting(BallotImportWarning::code)
                .contains("POSITION_COUNT", "UNRECOGNIZED_POSITION_LINES");
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
        Participant secondVoter = participant(contestId, "SecondVoter", "SE");
        Participant notVoter = participant(contestId, "NotVoter", "NO");
        long showId = id(post("/api/contests/" + contestId + "/shows", "{\"showNumber\":3,\"name\":\"Archivthema\"}").body(), "id");

        StringBuilder entries = new StringBuilder("{\"entries\":[");
        for (int index = 0; index < rankedSongs.size(); index++) {
            if (index > 0) entries.append(',');
            Song song = rankedSongs.get(index);
            entries.append("{\"artist\":\"").append(json(song.artist())).append("\",\"title\":\"").append(json(song.title()))
                    .append("\",\"youtubeUrl\":null,\"participantId\":").append(submitters.get(index).participantId()).append('}');
        }
        entries.append(",{\"artist\":\"Own Artist\",\"title\":\"Own Song\",\"youtubeUrl\":null,\"participantId\":").append(voter.participantId()).append('}');
        entries.append(",{\"artist\":\"Outside Artist\",\"title\":\"Outside Song\",\"youtubeUrl\":null,\"participantId\":").append(outside.participantId()).append('}');
        entries.append(",{\"artist\":\"Zero Artist\",\"title\":\"Zero Song\",\"youtubeUrl\":null,\"participantId\":").append(notVoter.participantId()).append("}]}");
        assertThat(post("/api/shows/" + showId + "/entries/historical-import", entries.toString()).statusCode()).isEqualTo(200);
        assertThat(post("/api/shows/" + showId + "/entries/entry-list/complete", "").statusCode()).isEqualTo(204);
        List<Long> entryIds = rankedSongs.stream().map(song -> jdbc.queryForObject(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = ? AND title = ?", Long.class, showId, song.artist(), song.title()
        )).toList();
        long ownEntryId = jdbc.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = 'Own Artist'", Long.class, showId);
        long outsideEntryId = jdbc.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = 'Outside Artist'", Long.class, showId);
        long zeroEntryId = jdbc.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = ? AND artist = 'Zero Artist'", Long.class, showId);
        return new Fixture(showId, voter.participationId(), secondVoter.participationId(), notVoter.participationId(), entryIds, ownEntryId, outsideEntryId, zeroEntryId);
    }

    private static List<PublishedBallotParticipant> japanParticipants() {
        List<PublishedBallotParticipant> participants = new ArrayList<>();
        participants.add(new PublishedBallotParticipant(1, 1, "Grissom", "JP", "Japan", List.of()));
        for (int index = 0; index < JAPAN_SONGS.size(); index++) {
            Song song = JAPAN_SONGS.get(index);
            participants.add(new PublishedBallotParticipant(
                    index + 2L, index + 2L, song.submitter(), song.countryCode(), song.countryCode(), List.of()
            ));
        }
        return List.copyOf(participants);
    }

    private static List<PublishedBallotEntry> japanEntries() {
        List<PublishedBallotEntry> entries = new ArrayList<>();
        for (int index = 0; index < JAPAN_SONGS.size(); index++) {
            Song song = JAPAN_SONGS.get(index);
            entries.add(new PublishedBallotEntry(
                    index + 1L, 1, song.artist(), song.title(), japanUrl(index), index + 2L, index + 2L,
                    song.submitter(), song.countryCode()
            ));
        }
        return List.copyOf(entries);
    }

    private static String japanMarkdown() {
        return JAPAN_FIXTURE
                .replaceFirst("1点 ", "**1点****")
                .replace("20点 Samoa - OMW ", "**20点****Samoa - OMW **")
                .replace("25ポイント Niederlande - Daniel. ", "***25ポイント*** **Niederlande - Daniel. **");
    }

    private static String japanRichHtml() {
        List<String> lines = JAPAN_FIXTURE.lines().filter(line -> !line.isBlank()).toList();
        StringBuilder html = new StringBuilder("<p><strong>").append(lines.getFirst()).append("</strong></p>");
        for (int index = 0; index < JAPAN_SONGS.size(); index++) {
            String line = lines.get(index + 1);
            Song song = JAPAN_SONGS.get(index);
            int contentStart = line.indexOf(' ') + 1;
            int songStart = line.indexOf(song.artist());
            html.append("<p><strong>").append(line, 0, contentStart - 1).append("</strong><strong>")
                    .append(line, contentStart, songStart).append("</strong><a href=\"").append(japanUrl(index))
                    .append("\">").append(line.substring(songStart)).append("</a></p>");
        }
        return html.toString();
    }

    private static String japanFixtureWithPositionCount(int positionCount) {
        List<String> lines = JAPAN_FIXTURE.lines().filter(line -> !line.isBlank()).toList();
        String text = String.join("\n", lines.subList(0, Math.min(positionCount + 1, lines.size())));
        return positionCount <= JAPAN_SONGS.size() ? text : text + "\n" + lines.get(1);
    }

    private static String japanUrl(int index) {
        return "https://source.test/japan/" + (index + 1);
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

    private static void assertStanding(String body, long entryId, int interimRank, int points, int mentions) {
        int entryStart = body.indexOf("\"entryId\":" + entryId + ',');
        int objectStart = body.lastIndexOf('{', entryStart);
        int objectEnd = body.indexOf('}', entryStart);
        assertThat(entryStart).isGreaterThanOrEqualTo(0);
        assertThat(body.substring(objectStart, objectEnd + 1)).contains(
                "\"interimRank\":" + interimRank,
                "\"points\":" + points,
                "\"mentions\":" + mentions
        );
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
    private record Fixture(
            long showId, long voterParticipationId, long secondVoterParticipationId, long notVoterParticipationId,
            List<Long> rankedEntryIds, long ownEntryId, long outsideEntryId, long zeroEntryId
    ) { }

    private static final List<Song> JAPAN_SONGS = List.of(
            new Song("Nik Kershaw", "The Riddle", "Fletcher Cox", "NR"),
            new Song("Eddie Murphy", "Party All The Time", "Berggorilla", "UG"),
            new Song("S Club", "Bring It All Back", "Jamie Hayter", "NZ"),
            new Song("The Weeknd", "Blinding Lights", "Mark Webber", "AU"),
            new Song("IVE", "I AM", "Die Ente", "VA"),
            new Song("Elton John", "I'm Still Standing", "Everton", "GR"),
            new Song("Millencolin", "Da Strike", "PrettyFlamingo", "CG"),
            new Song("Roger Whittaker", "Ein bisschen Aroma", "Contiomagus", "ZA"),
            new Song("Linkin Park", "Somewhere I Belong", "Kenny Ospreay", "LU"),
            new Song("P!nk", "Get The Party Started", "Scott D'Amore", "BA"),
            new Song("Gloria Gaynor", "I Will Survive", "snaggletooth", "XS"),
            new Song("Red Hot Chilli Peppers", "One Way Traffic", "The Red-NGA Shankmos", "NG"),
            new Song("Goldfinger", "Superman", "Dr. King Schultz", "KR"),
            new Song("Farin Urlaub Racing Team", "Am Strand", "OMW", "WS"),
            new Song("Adam Green", "Emily", "Daniel.", "NL")
    );

    // Real source order is canonical: the attached Japanese point words are opaque decoration, not rank values.
    private static final String JAPAN_FIXTURE = """
            [#1 ]Japan - Grissom

            1点 Nauru - Fletcher Cox Nik Kershaw - The Riddle
            2点 Uganda - Berggorilla Eddie Murphy - Party All The Time
            3点 Neuseeland - Jamie Hayter S Club - Bring It All Back
            4点 Australien - Mark Webber The Weeknd - Blinding Lights
            5点 Vatikanstadt - Die Ente IVE - I AM
            6点 Griechenland - Everton Elton John - I'm Still Standing
            7点 Kongo - PrettyFlamingo Millencolin - Da Strike
            8点 Südafrika - Contiomagus Roger Whittaker - Ein bisschen Aroma
            9点 Luxemburg - Kenny Ospreay Linkin Park - Somewhere I Belong
            10点 Bosnien und Herzegowina - Scott D'Amore P!nk - Get The Party Started
            11点 Schottland - snaggletooth Gloria Gaynor - I Will Survive
            13点 Nigeria - The Red-NGA Shankmos Red Hot Chilli Peppers - One Way Traffic
            16点 Südkorea - Dr. King Schultz Goldfinger - Superman
            20点 Samoa - OMW Farin Urlaub Racing Team - Am Strand
            25ポイント Niederlande - Daniel. Adam Green - Emily
            """;

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
