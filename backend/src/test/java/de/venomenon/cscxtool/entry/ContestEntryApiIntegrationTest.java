package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContestEntryApiIntegrationTest {

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
    void supportsScopedManualCrudNormalizationAndSeparateAssessments() throws Exception {
        long entryId = create(1, "  Interpret  ", "  Titel  ", "https://youtu.be/dQw4w9WgXcQ?t=42", " Notiz ");

        HttpResponse<String> list = get("/api/shows/1/entries");
        assertThat(list.statusCode()).isEqualTo(200);
        assertThat(list.body()).contains(
                "\"artist\":\"Interpret\"", "\"title\":\"Titel\"",
                "\"youtubeUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=42\"",
                "\"assessment\":null", "\"assessmentConfidence\":null"
        );
        assertThat(get("/api/shows/6/entries").body()).isEqualTo("[]");

        HttpResponse<String> foreignUpdate = patch("/api/shows/6/entries/" + entryId, entryJson("Other", "Song", "https://youtu.be/dQw4w9WgXcQ", null));
        assertThat(foreignUpdate.statusCode()).isEqualTo(404);
        assertThat(foreignUpdate.body()).contains("CONTEST_ENTRY_NOT_FOUND");

        HttpResponse<String> updated = patch("/api/shows/1/entries/" + entryId,
                entryJson("Geändert", "Titel", "https://youtube.com/embed/dQw4w9WgXcQ", "Hörnotiz"));
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"assessment\":null", "\"assessmentConfidence\":null", "\"comment\":\"Hörnotiz\"");
        assertThat(delete("/api/shows/1/entries/" + entryId).statusCode()).isEqualTo(204);
    }

    @Test
    void setsChangesAndResetsAssessmentWithoutChangingMetadataOrOrders() throws Exception {
        long entryId = create(1, "Interpret", "Titel", "https://youtu.be/dQw4w9WgXcQ", "Notiz");
        jdbcTemplate.update("UPDATE contest_entry SET ranking_position = 1 WHERE id = ?", entryId);

        HttpResponse<String> initial = patch("/api/shows/1/entries/" + entryId + "/assessment", "{\"assessment\":4,\"assessmentConfidence\":1}");
        assertThat(initial.statusCode()).isEqualTo(200);
        assertThat(initial.body()).contains("\"assessment\":4", "\"assessmentConfidence\":1", "\"rankingPosition\":1", "\"comment\":\"Notiz\"");
        assertThat(get("/api/shows").body()).contains("\"assessedEntryCount\":1");

        HttpResponse<String> changed = patch("/api/shows/1/entries/" + entryId + "/assessment", "{\"assessment\":2,\"assessmentConfidence\":5}");
        assertThat(changed.statusCode()).isEqualTo(200);
        assertThat(changed.body()).contains("\"assessment\":2", "\"assessmentConfidence\":5");
        assertThat(jdbcTemplate.queryForObject("SELECT pool_position FROM contest_entry WHERE id = ?", Integer.class, entryId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT ranking_position FROM contest_entry WHERE id = ?", Integer.class, entryId)).isEqualTo(1);

        HttpResponse<String> reset = patch("/api/shows/1/entries/" + entryId + "/assessment", "{\"assessment\":null,\"assessmentConfidence\":null}");
        assertThat(reset.statusCode()).isEqualTo(200);
        assertThat(reset.body()).contains("\"assessment\":null", "\"assessmentConfidence\":null");
        assertThat(get("/api/shows").body()).contains("\"assessedEntryCount\":0");
    }

    @Test
    void rejectsIncompleteAndOutOfRangeAssessmentPairs() throws Exception {
        long entryId = create(1, "Interpret", "Titel", "https://youtu.be/dQw4w9WgXcQ", null);
        for (String invalid : new String[] {
                "{\"assessment\":3,\"assessmentConfidence\":null}",
                "{\"assessment\":null,\"assessmentConfidence\":3}",
                "{\"assessment\":null}",
                "{\"assessmentConfidence\":null}",
                "{\"assessment\":0,\"assessmentConfidence\":2}",
                "{\"assessment\":5,\"assessmentConfidence\":6}"
        }) {
            HttpResponse<String> response = patch("/api/shows/1/entries/" + entryId + "/assessment", invalid);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).contains("INVALID_ENTRY_ASSESSMENT");
        }
    }

    @Test
    void exposesPreviewWithoutPersistingClipboardDataAndMarksExistingDuplicates() throws Exception {
        create(2, "Imminence", "Paralyzed", "https://youtu.be/2Dqu1Gh45qU", null);
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = 2", Integer.class);

        HttpResponse<String> preview = post("/api/shows/2/entries/import-preview", """
                {"html":"<a href=\\"https://www.youtube.com/watch?v=2Dqu1Gh45qU\\">Imminence - Paralyzed</a><p>Ohne Link - sichtbar</p>",
                 "text":"Imminence - Paralyzed -> https://www.youtube.com/watch?v=2Dqu1Gh45qU\\nOhne Link - sichtbar"}
                """);

        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"sourceType\":\"HTML_LINK\"", "\"possibleDuplicate\":true", "POSSIBLE_DUPLICATE", "Ohne Link - sichtbar");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = 2", Integer.class)).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('import_session', 'import_row')", Integer.class)).isZero();
    }

    @Test
    void importsExactlyTheConfirmedEntriesInOrderAndRollsBackTheWholeInvalidImport() throws Exception {
        HttpResponse<String> imported = post("/api/shows/3/entries/import", """
                {"entries":[
                  {"artist":"First","title":"Song","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":null},
                  {"artist":"Second","title":"Song","youtubeUrl":"https://youtube.com/shorts/9bZkp7q19f0","comment":"Notiz"}
                ]}
                """);
        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(imported.body().indexOf("\"artist\":\"First\""))
                .isLessThan(imported.body().indexOf("\"artist\":\"Second\""));
        assertThat(imported.body()).contains("\"assessment\":null", "\"assessmentConfidence\":null", "\"rankingPosition\":null", "\"participantId\":null");

        HttpResponse<String> invalidImport = post("/api/shows/4/entries/import", """
                {"entries":[
                  {"artist":"Would be inserted","title":"First","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":null},
                  {"artist":"Broken","title":"Second","youtubeUrl":"https://example.test/not-youtube","comment":null}
                ]}
                """);
        assertThat(invalidImport.statusCode()).isEqualTo(400);
        assertThat(get("/api/shows/4/entries").body()).isEqualTo("[]");
    }

    @Test
    void keepsPoolAndRankingOrdersIndependentAcrossReorderAndDelete() throws Exception {
        long first = create(7, "First", "A", "https://youtu.be/dQw4w9WgXcQ", null);
        long second = create(7, "Second", "B", "https://youtu.be/9bZkp7q19f0", null);
        long third = create(7, "Third", "C", "https://youtu.be/2Dqu1Gh45qU", null);
        jdbcTemplate.update("UPDATE contest_entry SET ranking_position = 1 WHERE id = ?", first);
        jdbcTemplate.update("UPDATE contest_entry SET ranking_position = 2 WHERE id = ?", second);

        HttpResponse<String> reordered = put("/api/shows/7/entries/reorder", "{\"entryIds\":[" + third + "," + first + "," + second + "]}");

        assertThat(reordered.statusCode()).isEqualTo(200);
        assertThat(reordered.body()).contains("\"poolPosition\":1", "\"poolPosition\":2", "\"poolPosition\":3");
        assertThat(jdbcTemplate.queryForList("SELECT id FROM contest_entry WHERE motto_show_id = 7 ORDER BY pool_position", Long.class))
                .containsExactly(third, first, second);
        assertThat(jdbcTemplate.queryForList("SELECT ranking_position FROM contest_entry WHERE id IN (?, ?) ORDER BY ranking_position", Integer.class, first, second))
                .containsExactly(1, 2);

        HttpResponse<String> invalid = put("/api/shows/7/entries/reorder", "{\"entryIds\":[" + first + "," + first + "," + second + "]}");
        assertThat(invalid.statusCode()).isEqualTo(409);
        assertThat(invalid.body()).contains("POOL_REORDER_CONFLICT");
        assertThat(jdbcTemplate.queryForList("SELECT id FROM contest_entry WHERE motto_show_id = 7 ORDER BY pool_position", Long.class))
                .containsExactly(third, first, second);

        assertThat(delete("/api/shows/7/entries/" + first).statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForList("SELECT pool_position FROM contest_entry WHERE motto_show_id = 7 ORDER BY pool_position", Integer.class))
                .containsExactly(1, 2);
        assertThat(jdbcTemplate.queryForList("SELECT ranking_position FROM contest_entry WHERE motto_show_id = 7 AND ranking_position IS NOT NULL", Integer.class))
                .containsExactly(1);
    }

    @Test
    void enforcesDatabaseIntegrityAndReturnsConflictForAReferencedParticipant() throws Exception {
        long entryId = create(5, "Referenz", "Beitrag", "https://youtu.be/dQw4w9WgXcQ", null);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, ranking_position, created_at, updated_at)
                VALUES (5, 'X', 'Y', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, created_at, updated_at)
                VALUES (999, 'X', 'Y', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);

        long participantId = firstId(post("/api/participants", "{" + "\"displayName\":\"Historisch\",\"countryCode\":\"DE\",\"active\":true}" ).body());
        jdbcTemplate.update("UPDATE contest_entry SET participant_id = ? WHERE id = ?", participantId, entryId);
        HttpResponse<String> deletion = delete("/api/participants/" + participantId);
        assertThat(deletion.statusCode()).isEqualTo(409);
        assertThat(deletion.body()).contains("\"code\":\"PARTICIPANT_IN_USE\"");
    }

    private long create(long showId, String artist, String title, String youtubeUrl, String comment) throws Exception {
        HttpResponse<String> response = post("/api/shows/" + showId + "/entries", entryJson(artist, title, youtubeUrl, comment));
        assertThat(response.statusCode()).isEqualTo(201);
        return firstId(response.body());
    }

    private static String entryJson(String artist, String title, String youtubeUrl, String comment) {
        return "{\"artist\":\"" + artist + "\",\"title\":\"" + title + "\",\"youtubeUrl\":\"" + youtubeUrl
                + "\",\"comment\":" + (comment == null ? "null" : "\"" + comment + "\"") + "}";
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

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-entry-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
