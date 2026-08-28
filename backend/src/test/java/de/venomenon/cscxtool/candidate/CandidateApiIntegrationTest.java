package de.venomenon.cscxtool.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CandidateApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void supportsCrudShowIsolationAndDatabaseConstraints() throws Exception {
        long first = create(10, "  Artist  ", "  Titel  ", "https://youtu.be/dQw4w9WgXcQ?t=42", " Kommentar ");
        long second = create(10, "Artist", "Titel", "https://youtube.com/shorts/9bZkp7q19f0", null);

        HttpResponse<String> list = get("/api/shows/10/candidates");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(
                "\"artist\":\"Artist\"", "\"title\":\"Titel\"", "\"status\":\"OFFEN\"",
                "\"youtubeUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42\"",
                "\"manualPosition\":1", "\"manualPosition\":2"
        );
        assertThat(get("/api/shows/11/candidates").body()).isEqualTo("[]");

        HttpResponse<String> foreignUpdate = patch("/api/shows/2/candidates/" + first, candidateJson("Other", "Other", "https://youtu.be/dQw4w9WgXcQ", "OFFEN", null));
        assertThat(foreignUpdate.statusCode()).isEqualTo(404);
        assertThat(foreignUpdate.body()).contains("\"code\":\"CANDIDATE_NOT_FOUND\"");

        HttpResponse<String> updated = patch("/api/shows/10/candidates/" + first,
                candidateJson("Geändert", "Titel", "https://youtube.com/embed/dQw4w9WgXcQ", "FINALIST", "Notiz"));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"status\":\"FINALIST\"", "\"artist\":\"Geändert\"");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (10, 'X', 'Y', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'UNGUELTIG', 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (10, ' ', 'Y', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (10, 'Duplikat', 'Y', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE motto_show SET selected_candidate_id = ? WHERE id = ?", first, 11
        )).isInstanceOf(DataAccessException.class);

        assertThat(delete("/api/shows/10/candidates/" + first).statusCode()).isEqualTo(204);
        assertThat(get("/api/shows/10/candidates").body()).contains("\"id\":" + second, "\"manualPosition\":1");
    }

    @Test
    void atomicallyReordersOnlyACompleteScopedList() throws Exception {
        long first = create(2, "A", "One", "https://youtu.be/dQw4w9WgXcQ", null);
        long second = create(2, "B", "Two", "https://youtu.be/9bZkp7q19f0", null);
        long third = create(2, "C", "Three", "https://youtu.be/3JZ_D3ELwOQ", null);
        long foreign = create(3, "D", "Four", "https://youtu.be/L_jWHffIx5E", null);

        HttpResponse<String> reordered = put("/api/shows/2/candidates/reorder", "{\"candidateIds\":[" + third + "," + first + "," + second + "]}");
        assertThat(reordered.statusCode()).isEqualTo(200);
        assertInOrder(reordered.body(), third, first, second);

        HttpResponse<String> missing = put("/api/shows/2/candidates/reorder", "{\"candidateIds\":[" + first + "," + second + "]}");
        HttpResponse<String> duplicate = put("/api/shows/2/candidates/reorder", "{\"candidateIds\":[" + first + "," + first + "," + second + "]}");
        HttpResponse<String> scoped = put("/api/shows/2/candidates/reorder", "{\"candidateIds\":[" + first + "," + second + "," + foreign + "]}");
        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(scoped.statusCode()).isEqualTo(409);
        assertThat(missing.body()).contains("\"code\":\"CANDIDATE_REORDER_CONFLICT\"");
        assertInOrder(get("/api/shows/2/candidates").body(), third, first, second);
    }

    @Test
    void copiesToEveryTargetOrToNoneAndKeepsCopiesIndependent() throws Exception {
        long source = create(4, "Quelle", "Song", "https://youtu.be/dQw4w9WgXcQ", "Kommentar");
        HttpResponse<String> copied = post("/api/shows/4/candidates/" + source + "/copy", "{\"targetShowIds\":[5,6]}");
        assertThat(copied.statusCode()).isEqualTo(201);
        assertThat(get("/api/shows/5/candidates").body()).contains("\"status\":\"OFFEN\"", "\"artist\":\"Quelle\"");
        assertThat(get("/api/shows/6/candidates").body()).contains("\"status\":\"OFFEN\"");

        long copyId = firstId(get("/api/shows/5/candidates").body());
        assertThat(patch("/api/shows/5/candidates/" + copyId,
                candidateJson("Kopie", "Song", "https://youtu.be/dQw4w9WgXcQ", "IM_RENNEN", "Neu")).statusCode()).isEqualTo(200);
        assertThat(get("/api/shows/4/candidates").body()).contains("\"artist\":\"Quelle\"");

        HttpResponse<String> invalidTargets = post("/api/shows/4/candidates/" + source + "/copy", "{\"targetShowIds\":[7,999]}");
        assertThat(invalidTargets.statusCode()).isEqualTo(400);
        assertThat(get("/api/shows/7/candidates").body()).isEqualTo("[]");
        assertThat(post("/api/shows/4/candidates/" + source + "/copy", "{\"targetShowIds\":[4]}").statusCode()).isEqualTo(400);
    }

    @Test
    void requiresConsciousSubmissionReplacementAndProtectsSelectedCandidate() throws Exception {
        long first = create(8, "Erste", "Einreichung", "https://youtu.be/dQw4w9WgXcQ", null);
        long second = create(8, "Zweite", "Einreichung", "https://youtu.be/9bZkp7q19f0", null);

        assertThat(put("/api/shows/8/submission", "{\"candidateId\":" + first + ",\"confirmReplacement\":false}").statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/8/submission", "{\"candidateId\":" + first + ",\"confirmReplacement\":false}").statusCode()).isEqualTo(200);
        HttpResponse<String> replacementNeedsConfirmation = put("/api/shows/8/submission", "{\"candidateId\":" + second + ",\"confirmReplacement\":false}");
        assertThat(replacementNeedsConfirmation.statusCode()).isEqualTo(409);
        assertThat(replacementNeedsConfirmation.body()).contains("SUBMISSION_REPLACEMENT_CONFIRMATION_REQUIRED");
        assertThat(delete("/api/shows/8/candidates/" + first).statusCode()).isEqualTo(409);

        assertThat(put("/api/shows/8/submission", "{\"candidateId\":" + second + ",\"confirmReplacement\":true}").statusCode()).isEqualTo(200);
        assertThat(get("/api/shows").body()).contains("\"candidateCount\":2", "\"selectedCandidate\":{\"id\":" + second);
        assertThat(delete("/api/shows/8/submission").statusCode()).isEqualTo(204);
        assertThat(delete("/api/shows/8/candidates/" + second).statusCode()).isEqualTo(204);
    }

    @Test
    void exposesConservativeCspOnTheLocalApplication() throws Exception {
        HttpResponse<String> response = get("/api/shows");
        assertThat(response.headers().firstValue("Content-Security-Policy")).hasValue(
                "default-src 'self'; base-uri 'none'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; frame-src https://www.youtube-nocookie.com; img-src 'self' data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'"
        );
    }

    @Test
    void persistsTheCompleteCandidateToSubmissionFlow() throws Exception {
        long candidateId = create(12, "Durchgehend", "Ablauf", "https://youtube.com/live/dQw4w9WgXcQ?start=75", null);
        assertThat(patch("/api/shows/12/candidates/" + candidateId,
                candidateJson("Durchgehend", "Bearbeitet", "https://youtube.com/live/dQw4w9WgXcQ?start=75", "ENGERE_AUSWAHL", "Kommentar")).statusCode())
                .isEqualTo(200);
        assertThat(put("/api/shows/12/candidates/reorder", "{\"candidateIds\":[" + candidateId + "]}").statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/12/submission", "{\"candidateId\":" + candidateId + ",\"confirmReplacement\":false}").statusCode()).isEqualTo(200);
        assertThat(get("/api/shows/12/candidates").body()).contains(
                "\"title\":\"Bearbeitet\"", "\"status\":\"ENGERE_AUSWAHL\"", "\"manualPosition\":1"
        );
        assertThat(get("/api/shows").body()).contains("\"selectedCandidate\":{\"id\":" + candidateId);

        DataSource reopenedDataSource = SqliteDataSourceFactory.create(STORAGE_ROOT.resolve("data/csc-x-tool.db"));
        JdbcTemplate reopenedJdbcTemplate = new JdbcTemplate(reopenedDataSource);
        assertThat(reopenedJdbcTemplate.queryForObject(
                "SELECT manual_position FROM candidate WHERE id = ?", Integer.class, candidateId
        )).isEqualTo(1);
        assertThat(reopenedJdbcTemplate.queryForObject(
                "SELECT selected_candidate_id FROM motto_show WHERE id = 12", Long.class
        )).isEqualTo(candidateId);
    }

    private long create(long showId, String artist, String title, String youtubeUrl, String comment) throws Exception {
        String commentJson = comment == null ? "null" : "\"" + comment + "\"";
        HttpResponse<String> response = post("/api/shows/" + showId + "/candidates", """
                {"artist":"%s","title":"%s","youtubeUrl":"%s","comment":%s}
                """.formatted(artist, title, youtubeUrl, commentJson));
        assertThat(response.statusCode()).isEqualTo(201);
        return firstId(response.body());
    }

    private static String candidateJson(String artist, String title, String youtubeUrl, String status, String comment) {
        return "{\"artist\":\"" + artist + "\",\"title\":\"" + title + "\",\"youtubeUrl\":\"" + youtubeUrl
                + "\",\"comment\":" + (comment == null ? "null" : "\"" + comment + "\"") + ",\"status\":\"" + status + "\"}";
    }

    private HttpResponse<String> get(String path) throws Exception {
        return request("GET", path, null);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return request("POST", path, body);
    }

    private HttpResponse<String> patch(String path, String body) throws Exception {
        return request("PATCH", path, body);
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        return request("PUT", path, body);
    }

    private HttpResponse<String> delete(String path) throws Exception {
        return request("DELETE", path, null);
    }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long firstId(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = body.indexOf(',', start);
        return Long.parseLong(body.substring(start, end));
    }

    private static void assertInOrder(String body, long... candidateIds) {
        int lastIndex = -1;
        for (long candidateId : candidateIds) {
            int index = body.indexOf("\"id\":" + candidateId);
            assertThat(index).isGreaterThan(lastIndex);
            lastIndex = index;
        }
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-candidate-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
