package de.venomenon.cscxtool.tips;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TipsGameApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final AtomicInteger FIXTURE_SEQUENCE = new AtomicInteger();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void keepsPartialDraftsAtomicAndSeparateFromActualAssignments() throws Exception {
        Fixture fixture = fixture();

        HttpResponse<String> readOnlyDraft = get("/api/shows/" + fixture.showId + "/tips");
        assertThat(readOnlyDraft.statusCode()).isEqualTo(200);
        assertThat(readOnlyDraft.body()).contains("\"persisted\":false", "\"status\":\"DRAFT\"");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tips_game WHERE motto_show_id = ?", Integer.class, fixture.showId)).isZero();

        String partial = assignments(fixture.firstEntryId, fixture.firstParticipationId, "HIGH", "mehrzeilige\nNotiz");
        HttpResponse<String> saved = put("/api/shows/" + fixture.showId + "/tips", partial);
        assertThat(saved.statusCode()).isEqualTo(200);
        assertThat(saved.body()).contains("\"persisted\":true", "\"confidence\":\"HIGH\"", "mehrzeilige\\nNotiz");
        assertThat(jdbc.queryForObject("SELECT contest_participation_id FROM contest_entry WHERE id = ?", Long.class, fixture.firstEntryId)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tips_game_assignment", Integer.class)).isEqualTo(1);

        HttpResponse<String> duplicateParticipant = put("/api/shows/" + fixture.showId + "/tips", "{\"assignments\":["
                + assignment(fixture.firstEntryId, fixture.firstParticipationId, null, null) + ","
                + assignment(fixture.secondEntryId, fixture.firstParticipationId, null, null) + "]}");
        assertThat(duplicateParticipant.statusCode()).isEqualTo(409);
        assertThat(duplicateParticipant.body()).contains("DUPLICATE_TIP_PARTICIPANT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tips_game_assignment", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT guessed_participation_id FROM tips_game_assignment", Long.class)).isEqualTo(fixture.firstParticipationId);

        HttpResponse<String> foreignEntry = put("/api/shows/" + fixture.showId + "/tips", assignments(fixture.foreignEntryId, fixture.secondParticipationId, null, null));
        assertThat(foreignEntry.statusCode()).isEqualTo(409);
        assertThat(foreignEntry.body()).contains("TIP_ENTRY_NOT_IN_SHOW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tips_game_assignment", Integer.class)).isEqualTo(1);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO tips_game_assignment (tips_game_id,contest_entry_id,guessed_participation_id,confidence,note)
                VALUES ((SELECT id FROM tips_game WHERE motto_show_id = ?), ?, ?, NULL, NULL)
                """, fixture.showId, fixture.foreignEntryId, fixture.secondParticipationId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void resolvesUsingCurrentActualAssignmentsAndReopensWithoutOverwritingTheTip() throws Exception {
        Fixture fixture = fixture();
        assertThat(put("/api/shows/" + fixture.showId + "/tips", "{\"assignments\":["
                + assignment(fixture.firstEntryId, fixture.firstParticipationId, "HIGH", null) + "]}").statusCode()).isEqualTo(200);

        HttpResponse<String> premature = post("/api/shows/" + fixture.showId + "/tips/resolve", "");
        assertThat(premature.statusCode()).isEqualTo(409);
        assertThat(premature.body()).contains("ACTUAL_ASSIGNMENTS_INCOMPLETE");

        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", fixture.firstParticipationId, fixture.firstEntryId);
        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", fixture.secondParticipationId, fixture.secondEntryId);

        HttpResponse<String> resolved = post("/api/shows/" + fixture.showId + "/tips/resolve", "");
        assertThat(resolved.statusCode()).isEqualTo(200);
        assertThat(resolved.body()).contains("\"status\":\"RESOLVED\"", "\"correct\":1", "\"missing\":1", "\"hitRate\":100.0");

        HttpResponse<String> locked = put("/api/shows/" + fixture.showId + "/tips", assignments(fixture.secondEntryId, fixture.secondParticipationId, null, null));
        assertThat(locked.statusCode()).isEqualTo(409);
        assertThat(locked.body()).contains("TIPS_GAME_RESOLVED");

        // This is the actual reveal correction. It changes the derived result only; the saved tip remains firstParticipationId.
        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", fixture.thirdParticipationId, fixture.firstEntryId);
        HttpResponse<String> corrected = get("/api/shows/" + fixture.showId + "/tips");
        assertThat(corrected.body()).contains("\"incorrect\":1", "\"guessedParticipationId\":" + fixture.firstParticipationId,
                "\"actualAssignment\":{\"participationId\":" + fixture.thirdParticipationId);

        HttpResponse<String> reopened = post("/api/shows/" + fixture.showId + "/tips/reopen", "");
        assertThat(reopened.statusCode()).isEqualTo(200);
        assertThat(reopened.body()).contains("\"status\":\"DRAFT\"", "\"guessedParticipationId\":" + fixture.firstParticipationId);
    }

    @Test
    void exportsTipsAndLoadsIdentityBasedSubmissionHistoryWithoutHeuristics() throws Exception {
        Fixture fixture = fixture();
        assertThat(put("/api/shows/" + fixture.showId + "/tips", assignments(fixture.firstEntryId, fixture.firstParticipationId, "LOW", "keine Genreannahme")).statusCode()).isEqualTo(200);
        long historicalContest = id(post("/api/contests", "{\"name\":\"CSC IX Tipp-Historie\"}").body(), "id");
        assertThat(post("/api/contests/" + historicalContest + "/participants", "{\"participantId\":" + fixture.firstParticipantId + ",\"countryCode\":\"AT\",\"active\":true}").statusCode()).isEqualTo(201);
        long historicalShow = id(post("/api/contests/" + historicalContest + "/shows", "{\"showNumber\":1,\"name\":\"Archiv\"}").body(), "id");
        assertThat(post("/api/shows/" + historicalShow + "/entries", "{\"artist\":\"Archivartist\",\"title\":\"Archivsong\",\"youtubeUrl\":null,\"comment\":null,\"participantId\":" + fixture.firstParticipantId + "}").statusCode()).isEqualTo(201);

        HttpResponse<String> history = get("/api/shows/" + fixture.showId + "/tips/participants/" + fixture.firstParticipationId + "/history");
        assertThat(history.statusCode()).isEqualTo(200);
        assertThat(history.body()).contains("Archivartist", "Archivsong", "\"countryCode\":\"AT\"", "\"currentContest\":false");
        assertThat(history.body()).doesNotContain("Genre", "Ausschluss");

        HttpResponse<String> csv = get("/api/data/export/tips-game.csv");
        assertThat(csv.statusCode()).isEqualTo(200);
        assertThat(csv.body()).contains("Tippspiel", "keine Genreannahme", "NOCH_NICHT_AUFGELOEST");
        HttpResponse<String> full = get("/api/data/export/full");
        assertThat(full.body()).contains("\"formatVersion\":8", "\"tipsGames\"", "\"tipsGameAssignments\"", "keine Genreannahme");
    }

    private Fixture fixture() throws Exception {
        int sequence = FIXTURE_SEQUENCE.incrementAndGet();
        String showName = "Tippspiel " + sequence;
        String foreignShowName = "Tippspiel Fremd " + sequence;
        jdbc.update("INSERT INTO motto_show (contest_id,show_number,name,created_at,updated_at) VALUES (1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", 100 + sequence, showName);
        jdbc.update("INSERT INTO motto_show (contest_id,show_number,name,created_at,updated_at) VALUES (1, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", 200 + sequence, foreignShowName);
        long showId = jdbc.queryForObject("SELECT id FROM motto_show WHERE name = ?", Long.class, showName);
        long foreignShowId = jdbc.queryForObject("SELECT id FROM motto_show WHERE name = ?", Long.class, foreignShowName);
        long firstParticipantId = id(post("/api/contests/1/participants", "{\"displayName\":\"Tipp Eins\",\"countryCode\":\"DE\",\"active\":true}").body(), "id");
        long firstParticipationId = jdbc.queryForObject("SELECT id FROM contest_participation WHERE contest_id = 1 AND participant_id = ?", Long.class, firstParticipantId);
        long secondParticipantId = id(post("/api/contests/1/participants", "{\"displayName\":\"Tipp Zwei\",\"countryCode\":\"FR\",\"active\":true}").body(), "id");
        long secondParticipationId = jdbc.queryForObject("SELECT id FROM contest_participation WHERE contest_id = 1 AND participant_id = ?", Long.class, secondParticipantId);
        long thirdParticipantId = id(post("/api/contests/1/participants", "{\"displayName\":\"Tipp Drei\",\"countryCode\":\"IT\",\"active\":true}").body(), "id");
        long thirdParticipationId = jdbc.queryForObject("SELECT id FROM contest_participation WHERE contest_id = 1 AND participant_id = ?", Long.class, thirdParticipantId);
        long firstEntry = id(post("/api/shows/" + showId + "/entries", entry("Song A", "Alpha")).body(), "id");
        long secondEntry = id(post("/api/shows/" + showId + "/entries", entry("Song B", "Beta")).body(), "id");
        long foreignEntry = id(post("/api/shows/" + foreignShowId + "/entries", entry("Song C", "Gamma")).body(), "id");
        return new Fixture(showId, firstParticipantId, firstParticipationId, secondParticipationId, thirdParticipationId, firstEntry, secondEntry, foreignEntry);
    }

    private static String entry(String artist, String title) {
        return "{\"artist\":\"" + artist + "\",\"title\":\"" + title + "\",\"youtubeUrl\":\"https://youtu.be/dQw4w9WgXcQ\",\"comment\":null}";
    }
    private static String assignments(long entryId, long participationId, String confidence, String note) {
        return "{\"assignments\":[" + assignment(entryId, participationId, confidence, note) + "]}";
    }
    private static String assignment(long entryId, long participationId, String confidence, String note) {
        return "{\"entryId\":" + entryId + ",\"guessedParticipationId\":" + participationId + ",\"confidence\":"
                + (confidence == null ? "null" : "\"" + confidence + "\"") + ",\"note\":" + (note == null ? "null" : "\"" + note.replace("\\", "\\\\").replace("\n", "\\n") + "\"") + "}";
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
    private HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body); }
    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null || body.isEmpty()) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    private static long id(String body, String field) {
        int start = body.indexOf("\"" + field + "\":") + field.length() + 3;
        int end = body.indexOf(',', start);
        if (end < 0) end = body.indexOf('}', start);
        return Long.parseLong(body.substring(start, end));
    }
    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-tips-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht erstellt werden.", exception); }
    }

    private record Fixture(long showId, long firstParticipantId, long firstParticipationId, long secondParticipationId, long thirdParticipationId,
                           long firstEntryId, long secondEntryId, long foreignEntryId) { }
}
