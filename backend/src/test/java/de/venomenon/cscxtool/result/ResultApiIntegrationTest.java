package de.venomenon.cscxtool.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ResultApiIntegrationTest {

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
    void completesTheP6FlowWithServerSideLocksAndHistoricalScores() throws Exception {
        long alpha = createParticipant("Alpha", true);
        long beta = createParticipant("Beta", true);
        long historic = createParticipant("Historisch", true);
        long inactive = createParticipant("Inaktiv", false);
        List<Long> entries = createEntries(1, 15);

        HttpResponse<String> mappingTooEarly = put("/api/shows/1/entries/" + entries.getFirst() + "/participant", "{\"participantId\":" + alpha + "}");
        assertThat(mappingTooEarly.statusCode()).isEqualTo(409);
        assertThat(mappingTooEarly.body()).contains("PARTICIPANT_ASSIGNMENT_REQUIRES_CLOSED_BALLOT");
        assertThat(put("/api/shows/3/results/scores/" + alpha, "{\"status\":\"ABGESTIMMT\",\"points\":0}").body())
                .contains("RESULTS_REQUIRE_CLOSED_BALLOT");

        put("/api/shows/1/ballot/reorder", reorderJson(entries, List.of()));
        assertThat(post("/api/shows/1/ballot/close", null).statusCode()).isEqualTo(200);

        assertThat(put("/api/shows/1/entries/" + entries.getFirst() + "/participant", "{\"participantId\":" + alpha + "}").statusCode())
                .isEqualTo(200);
        HttpResponse<String> duplicateAssignment = put("/api/shows/1/entries/" + entries.get(1) + "/participant", "{\"participantId\":" + alpha + "}");
        assertThat(duplicateAssignment.statusCode()).isEqualTo(409);
        assertThat(duplicateAssignment.body()).contains("PARTICIPANT_ALREADY_ASSIGNED_IN_SHOW");
        assertThat(put("/api/shows/1/entries/" + entries.get(1) + "/participant", "{\"participantId\":" + beta + "}").statusCode())
                .isEqualTo(200);
        HttpResponse<String> inactiveAssignment = put("/api/shows/1/entries/" + entries.get(2) + "/participant", "{\"participantId\":" + inactive + "}");
        assertThat(inactiveAssignment.statusCode()).isEqualTo(409);
        assertThat(inactiveAssignment.body()).contains("INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED");
        jdbcTemplate.update("UPDATE motto_show SET ballot_closed_at = CURRENT_TIMESTAMP WHERE id = 2");
        long otherShowEntry = createEntries(2, 1).getFirst();
        assertThat(put("/api/shows/2/entries/" + otherShowEntry + "/participant", "{\"participantId\":" + alpha + "}").statusCode())
                .isEqualTo(200);

        HttpResponse<String> firstRead = get("/api/shows/1/results");
        assertThat(firstRead.statusCode()).isEqualTo(200);
        assertThat(firstRead.body()).contains("\"displayName\":\"Alpha\"", "\"status\":\"UNBEKANNT\"", "\"persisted\":false");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM received_score WHERE motto_show_id = 1", Integer.class)).isZero();
        createAndSelectSubmission(1);
        HttpResponse<String> closeWithUnknownActiveParticipant = post("/api/shows/1/results/close", null);
        assertThat(closeWithUnknownActiveParticipant.statusCode()).isEqualTo(409);
        assertThat(closeWithUnknownActiveParticipant.body()).contains("RESULTS_CLOSE_REQUIRES_KNOWN_ACTIVE_SCORES");
        assertThat(put("/api/shows/1/results/details", "{\"officialTotalPoints\":null,\"finalPlace\":null,\"finalPlaceTied\":true}").body())
                .contains("TIED_FINAL_PLACE_REQUIRES_FINAL_PLACE");
        assertThat(put("/api/shows/1/results/scores/" + alpha, "{\"status\":\"UNBEKANNT\",\"points\":0}").statusCode()).isEqualTo(400);
        assertThat(put("/api/shows/1/results/scores/" + alpha, "{\"status\":\"ABGESTIMMT\",\"points\":12}").statusCode()).isEqualTo(400);
        assertThat(put("/api/shows/1/results/scores/" + alpha, "{\"status\":\"ABGESTIMMT\",\"points\":0}").statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/1/results/scores/" + beta, "{\"status\":\"NICHT_ABGESTIMMT\",\"points\":null}").statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/1/results/scores/" + historic, "{\"status\":\"ABGESTIMMT\",\"points\":5}").statusCode()).isEqualTo(200);
        jdbcTemplate.update("UPDATE participant SET active = 0 WHERE id = ?", historic);
        jdbcTemplate.update("UPDATE participant SET active = 0 WHERE id = ?", beta);
        assertThat(get("/api/shows/1/entries").body()).contains("\"id\":" + entries.get(1), "\"participantId\":" + beta);
        assertThat(put("/api/shows/1/entries/" + entries.get(1) + "/participant", "{\"participantId\":null}").statusCode()).isEqualTo(200);

        HttpResponse<String> scored = get("/api/shows/1/results");
        assertThat(scored.body()).contains("\"status\":\"ABGESTIMMT\",\"points\":0", "\"status\":\"NICHT_ABGESTIMMT\",\"points\":null");
        assertThat(scored.body()).contains("\"displayName\":\"Historisch\"", "\"active\":false", "\"calculatedTotalPoints\":5");
        assertThat(delete("/api/participants/" + historic).body()).contains("PARTICIPANT_IN_USE");

        assertThat(post("/api/shows/1/results/close", null).body()).contains("RESULTS_CLOSE_REQUIRES_FINAL_PLACE");
        HttpResponse<String> details = put("/api/shows/1/results/details", "{\"officialTotalPoints\":7,\"finalPlace\":3,\"finalPlaceTied\":true}");
        assertThat(details.statusCode()).isEqualTo(200);
        assertThat(details.body()).contains("\"calculatedTotalPoints\":5", "\"officialTotalPoints\":7", "\"officialTotalDifference\":2", "\"finalPlace\":3", "\"finalPlaceTied\":true");

        HttpResponse<String> closed = post("/api/shows/1/results/close", null);
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(closed.body()).contains("\"resultsClosedAt\":");
        assertThat(put("/api/shows/1/results/scores/" + alpha, "{\"status\":\"ABGESTIMMT\",\"points\":1}").body())
                .contains("RESULTS_REOPEN_REQUIRED");
        assertThat(delete("/api/shows/1/submission").body()).contains("RESULTS_REOPEN_REQUIRED_FOR_SUBMISSION_CHANGE");
        assertThat(post("/api/shows/1/ballot/reopen", null).body()).contains("RESULTS_REOPEN_REQUIRED_BEFORE_BALLOT_REOPEN");

        HttpResponse<String> reopenedResults = post("/api/shows/1/results/reopen", null);
        assertThat(reopenedResults.statusCode()).isEqualTo(200);
        assertThat(reopenedResults.body()).contains("\"resultsClosedAt\":null", "\"calculatedTotalPoints\":5", "\"officialTotalPoints\":7", "\"finalPlace\":3");
        assertThat(post("/api/shows/1/ballot/reopen", null).statusCode()).isEqualTo(200);
        assertThat(put("/api/shows/1/results/scores/" + alpha, "{\"status\":\"ABGESTIMMT\",\"points\":1}").body())
                .contains("RESULTS_REQUIRE_CLOSED_BALLOT");
    }

    private long createParticipant(String displayName, boolean active) throws Exception {
        HttpResponse<String> response = post("/api/participants", "{\"displayName\":\"" + displayName + "\",\"countryCode\":\"DE\",\"active\":" + active + "}");
        assertThat(response.statusCode()).isEqualTo(201);
        return firstId(response.body());
    }

    private List<Long> createEntries(long showId, int count) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            HttpResponse<String> response = post("/api/shows/" + showId + "/entries", """
                    {"artist":"Artist %d","title":"Song %d","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":null}
                    """.formatted(index, index));
            assertThat(response.statusCode()).isEqualTo(201);
            ids.add(firstId(response.body()));
        }
        return ids;
    }

    private void createAndSelectSubmission(long showId) throws Exception {
        jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (?, 'Eigene Einreichung', 'Mein Song', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'FINALIST', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, showId);
        long candidateId = jdbcTemplate.queryForObject("SELECT id FROM candidate WHERE motto_show_id = ?", Long.class, showId);
        assertThat(put("/api/shows/" + showId + "/submission", "{\"candidateId\":" + candidateId + ",\"confirmReplacement\":false}").statusCode())
                .isEqualTo(200);
    }

    private static String reorderJson(List<Long> rankedEntryIds, List<Long> unrankedEntryIds) {
        return "{\"rankedEntryIds\":" + rankedEntryIds + ",\"unrankedEntryIds\":" + unrankedEntryIds + "}";
    }

    private static long firstId(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = body.indexOf(',', start);
        return Long.parseLong(body.substring(start, end));
    }

    private HttpResponse<String> get(String path) throws Exception {
        return request("GET", path, null);
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return request("POST", path, body);
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

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-result-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
