package de.venomenon.cscxtool.entry;

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
class OwnEntryWorkflowApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void requiresAnExplicitResolutionAndAtomicallyExcludesTheMarkedOwnEntryFromRankingAndSnapshot() throws Exception {
        long showId = createShow();
        List<Long> entryIds = createEntries(showId, 16);
        long participantId = id(post("/api/contests/1/participants", """
                {"displayName":"Eigener Test","countryCode":"DE","active":true}
                """).body());
        long participationId = jdbc.queryForObject(
                "SELECT id FROM contest_participation WHERE contest_id = 1 AND participant_id = ?", Long.class, participantId);
        assertThat(put("/api/contests/1/own-participation", "{\"participationId\":" + participationId + ",\"confirmChange\":false}").statusCode())
                .isEqualTo(200);

        assertThat(put("/api/shows/" + showId + "/ballot/reorder", ranking(entryIds)).statusCode()).isEqualTo(200);
        HttpResponse<String> unresolvedClose = post("/api/shows/" + showId + "/ballot/close", null);
        assertThat(unresolvedClose.statusCode()).isEqualTo(409);
        assertThat(unresolvedClose.body()).contains("OWN_ENTRY_RESOLUTION_REQUIRED");

        long ownEntryId = entryIds.getFirst();
        HttpResponse<String> confirmationRequired = put("/api/shows/" + showId + "/entries/own-entry-resolution",
                "{\"resolution\":\"OWN_ENTRY\",\"entryId\":" + ownEntryId + ",\"confirmRankingRemoval\":false}");
        assertThat(confirmationRequired.statusCode()).isEqualTo(409);
        assertThat(confirmationRequired.body()).contains("OWN_ENTRY_RANKING_REMOVAL_CONFIRMATION_REQUIRED");

        assertThat(put("/api/shows/" + showId + "/entries/own-entry-resolution",
                "{\"resolution\":\"OWN_ENTRY\",\"entryId\":" + ownEntryId + ",\"confirmRankingRemoval\":true}").statusCode()).isEqualTo(204);
        HttpResponse<String> entries = get("/api/shows/" + showId + "/entries");
        assertThat(entries.body()).contains("\"id\":" + ownEntryId, "\"ownEntry\":true", "\"rankingPosition\":null");

        HttpResponse<String> tipsDraft = get("/api/shows/" + showId + "/tips");
        assertThat(tipsDraft.body()).contains("\"ownEntry\":true", "\"actualAssignment\":null");
        HttpResponse<String> ownTip = put("/api/shows/" + showId + "/tips", "{\"assignments\":[{\"entryId\":"
                + ownEntryId + ",\"guessedParticipationId\":" + participationId + ",\"confidence\":null,\"note\":null}]}");
        assertThat(ownTip.statusCode()).isEqualTo(409);
        assertThat(ownTip.body()).contains("OWN_ENTRY_CANNOT_BE_TIPPED");

        HttpResponse<String> reintroduceOwnEntry = put("/api/shows/" + showId + "/ballot/reorder", ranking(entryIds));
        assertThat(reintroduceOwnEntry.statusCode()).isEqualTo(409);
        assertThat(reintroduceOwnEntry.body()).contains("OWN_ENTRY_CANNOT_BE_RANKED");

        HttpResponse<String> closed = post("/api/shows/" + showId + "/ballot/close", null);
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ballot_snapshot_item WHERE contest_entry_id = ?", Integer.class, ownEntryId))
                .isZero();

        long noOwnEntryShowId = createShow(902);
        List<Long> noOwnEntryIds = createEntries(noOwnEntryShowId, 15);
        assertThat(put("/api/shows/" + noOwnEntryShowId + "/entries/own-entry-resolution",
                "{\"resolution\":\"NO_OWN_ENTRY\",\"entryId\":null,\"confirmRankingRemoval\":false}").statusCode()).isEqualTo(204);
        assertThat(put("/api/shows/" + noOwnEntryShowId + "/ballot/reorder", ranking(noOwnEntryIds)).statusCode()).isEqualTo(200);
        assertThat(post("/api/shows/" + noOwnEntryShowId + "/ballot/close", null).statusCode()).isEqualTo(200);
    }

    private long createShow() {
        return createShow(901);
    }

    private long createShow(int showNumber) {
        jdbc.update("""
                INSERT INTO motto_show (contest_id,show_number,name,created_at,updated_at)
                VALUES (1, ?, 'Eigene Einreichung', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, showNumber);
        return jdbc.queryForObject("SELECT id FROM motto_show WHERE contest_id = 1 AND show_number = ?", Long.class, showNumber);
    }

    private List<Long> createEntries(long showId, int count) throws Exception {
        List<Long> result = new ArrayList<>();
        for (int index = 1; index <= count; index++) {
            HttpResponse<String> created = post("/api/shows/" + showId + "/entries", """
                    {"artist":"Artist %d","title":"Song %d","youtubeUrl":"https://youtu.be/dQw4w9WgXcQ","comment":null}
                    """.formatted(index, index));
            assertThat(created.statusCode()).isEqualTo(201);
            result.add(id(created.body()));
        }
        return result;
    }

    private static String ranking(List<Long> entryIds) {
        return "{\"rankedEntryIds\":" + entryIds + ",\"unrankedEntryIds\":[]}";
    }

    private long id(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = body.indexOf(',', start);
        return Long.parseLong(body.substring(start, end));
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

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-own-entry-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht erstellt werden.", exception); }
    }
}
