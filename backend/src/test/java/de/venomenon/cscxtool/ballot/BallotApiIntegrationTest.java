package de.venomenon.cscxtool.ballot;

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
class BallotApiIntegrationTest {

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
    void atomicallyReordersTheCompleteTwoListStateAndRejectsConflictingStatesWithoutChangingSQLite() throws Exception {
        List<Long> entries = createEntries(1, 4);
        long foreign = createEntries(2, 1).getFirst();

        HttpResponse<String> reordered = put("/api/shows/1/ballot/reorder", reorderJson(
                List.of(entries.get(2), entries.get(0)), List.of(entries.get(3), entries.get(1))
        ));
        assertThat(reordered.statusCode()).isEqualTo(200);
        assertThat(reordered.body()).contains(
                "\"rankedEntryIds\":[" + entries.get(2) + "," + entries.get(0) + "]",
                "\"unrankedEntryIds\":[" + entries.get(3) + "," + entries.get(1) + "]"
        );
        assertRanking(1, List.of(entries.get(2), entries.get(0)));

        HttpResponse<String> missing = put("/api/shows/1/ballot/reorder", reorderJson(
                List.of(entries.get(0)), List.of(entries.get(1), entries.get(3))
        ));
        HttpResponse<String> duplicate = put("/api/shows/1/ballot/reorder", reorderJson(
                List.of(entries.get(0), entries.get(0)), List.of(entries.get(1), entries.get(2), entries.get(3))
        ));
        HttpResponse<String> foreignEntry = put("/api/shows/1/ballot/reorder", reorderJson(
                List.of(entries.get(0)), List.of(entries.get(1), entries.get(2), foreign, entries.get(3))
        ));

        assertThat(missing.statusCode()).isEqualTo(409);
        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(foreignEntry.statusCode()).isEqualTo(409);
        assertThat(missing.body()).contains("BALLOT_REORDER_CONFLICT");
        assertRanking(1, List.of(entries.get(2), entries.get(0)));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = 1 AND ranking_position IS NULL", Integer.class
        )).isEqualTo(2);
    }

    @Test
    void closesOnlyAValidTopFifteenKeepsSnapshotsImmutableAndReopensAtomically() throws Exception {
        List<Long> entries = createEntries(3, 16);
        put("/api/shows/3/ballot/reorder", reorderJson(entries.subList(0, 14), entries.subList(14, 16)));
        HttpResponse<String> tooShort = post("/api/shows/3/ballot/close", null);
        assertThat(tooShort.statusCode()).isEqualTo(409);
        assertThat(tooShort.body()).contains("BALLOT_CLOSE_REQUIRES_TOP_15");
        assertThat(jdbcTemplate.queryForObject("SELECT ballot_closed_at FROM motto_show WHERE id = 3", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ballot_snapshot WHERE motto_show_id = 3", Integer.class)).isZero();

        put("/api/shows/3/ballot/reorder", reorderJson(entries.subList(0, 15), List.of(entries.get(15))));
        HttpResponse<String> closed = post("/api/shows/3/ballot/close", null);
        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(closed.body()).contains("\"snapshotNumber\":1", "\"renderedText\":null");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ballot_snapshot_item WHERE ballot_snapshot_id = 1", Integer.class)).isEqualTo(15);

        HttpResponse<String> changedText = patch("/api/shows/3/entries/" + entries.getFirst(), entryJson("Korrigiert", "Neuer Titel", true));
        assertThat(changedText.statusCode()).isEqualTo(200);
        HttpResponse<String> unchangedSnapshot = get("/api/shows/3/ballot");
        assertThat(unchangedSnapshot.body()).contains("\"artist\":\"Artist 1\"", "\"title\":\"Song 1\"");

        HttpResponse<String> closedReorder = put("/api/shows/3/ballot/reorder", reorderJson(entries.subList(1, 16), List.of(entries.getFirst())));
        HttpResponse<String> closedDeletion = delete("/api/shows/3/entries/" + entries.getFirst());
        assertThat(closedReorder.statusCode()).isEqualTo(409);
        assertThat(closedDeletion.statusCode()).isEqualTo(409);
        assertThat(closedReorder.body()).contains("BALLOT_REOPEN_REQUIRED");

        HttpResponse<String> reopened = post("/api/shows/3/ballot/reopen", null);
        assertThat(reopened.statusCode()).isEqualTo(200);
        assertThat(reopened.body()).contains("\"currentSnapshot\":null", "\"current\":false");
        assertThat(jdbcTemplate.queryForObject("SELECT ballot_closed_at FROM motto_show WHERE id = 3", String.class)).isNull();

        assertThat(delete("/api/shows/3/entries/" + entries.getFirst()).statusCode()).isEqualTo(204);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM ballot_snapshot_item
                WHERE contest_entry_id IS NULL AND artist_snapshot = 'Artist 1' AND title_snapshot = 'Song 1'
                """, Integer.class)).isEqualTo(1);
        assertRanking(3, entries.subList(1, 15));

        put("/api/shows/3/ballot/reorder", reorderJson(entries.subList(1, 16), List.of()));
        HttpResponse<String> closedAgain = post("/api/shows/3/ballot/close", null);
        assertThat(closedAgain.statusCode()).isEqualTo(200);
        assertThat(closedAgain.body()).contains("\"snapshotNumber\":2", "\"snapshotNumber\":1", "\"current\":true", "\"current\":false", "\"renderedText\":null");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ballot_snapshot WHERE motto_show_id = 3 AND is_current = 1", Integer.class)).isEqualTo(1);

        HttpResponse<String> incompleteExport = get("/api/shows/3/ballot/export");
        assertThat(incompleteExport.statusCode()).isEqualTo(409);
        assertThat(incompleteExport.body()).contains("BALLOT_EXPORT_REQUIRES_PARTICIPANT_ASSIGNMENTS");

        assignParticipants(entries.subList(1, 16));
        HttpResponse<String> completedBallot = get("/api/shows/3/ballot");
        assertThat(completedBallot.body()).contains(
                "\"renderedText\":\"Platz #1 - Schottland: Artist 2 - Song 2\\nPlatz #2 - Kap Verde: Artist 3 - Song 3\\nPlatz #3 - Kongo: Artist 4 - Song 4"
        );

        HttpResponse<String> export = get("/api/shows/3/ballot/export");
        assertThat(export.statusCode()).isEqualTo(200);
        assertThat(export.headers().firstValue("content-type").orElse("")).startsWith("text/plain;charset=UTF-8");
        assertThat(export.body())
                .startsWith("Platz #1 - Schottland: Artist 2 - Song 2\nPlatz #2 - Kap Verde: Artist 3 - Song 3\nPlatz #3 - Kongo: Artist 4 - Song 4")
                .contains("Platz #15 - Deutschland: Artist 16 - Song 16")
                .doesNotContain("Punkte");
    }

    @Test
    void calculatesTheFixedPointsForEveryTopFifteenRank() {
        assertThat(List.of(
                BallotPoints.pointsForRank(1), BallotPoints.pointsForRank(2), BallotPoints.pointsForRank(3),
                BallotPoints.pointsForRank(4), BallotPoints.pointsForRank(5), BallotPoints.pointsForRank(6),
                BallotPoints.pointsForRank(7), BallotPoints.pointsForRank(8), BallotPoints.pointsForRank(9),
                BallotPoints.pointsForRank(10), BallotPoints.pointsForRank(11), BallotPoints.pointsForRank(12),
                BallotPoints.pointsForRank(13), BallotPoints.pointsForRank(14), BallotPoints.pointsForRank(15)
        )).containsExactly(25, 20, 16, 13, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1);
    }

    private void assignParticipants(List<Long> entryIds) {
        List<String> countryCodes = List.of("XS", "CV", "CG", "DE", "DE", "DE", "DE", "DE", "DE", "DE", "DE", "DE", "DE", "DE", "DE");
        for (int index = 0; index < entryIds.size(); index++) {
            long entryId = entryIds.get(index);
            long participantId = 1_000_000L + entryId;
            jdbcTemplate.update("""
                    INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                    VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, participantId, "Participant " + (index + 1), countryCodes.get(index));
            assertThat(jdbcTemplate.update(
                    "UPDATE contest_entry SET participant_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    participantId,
                    entryId
            )).isEqualTo(1);
        }
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

    private void assertRanking(long showId, List<Long> rankedEntryIds) {
        List<Long> ids = jdbcTemplate.query("""
                SELECT id FROM contest_entry
                WHERE motto_show_id = ? AND ranking_position IS NOT NULL
                ORDER BY ranking_position
                """, (resultSet, rowNumber) -> resultSet.getLong(1), showId);
        assertThat(ids).containsExactlyElementsOf(rankedEntryIds);
        assertThat(jdbcTemplate.query("""
                SELECT ranking_position FROM contest_entry
                WHERE motto_show_id = ? AND ranking_position IS NOT NULL
                ORDER BY ranking_position
                """, (resultSet, rowNumber) -> resultSet.getInt(1), showId))
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, rankedEntryIds.size()).boxed().toList());
    }

    private static String reorderJson(List<Long> rankedEntryIds, List<Long> unrankedEntryIds) {
        return "{\"rankedEntryIds\":" + rankedEntryIds + ",\"unrankedEntryIds\":" + unrankedEntryIds + "}";
    }

    private static String entryJson(String artist, String title, boolean listened) {
        return "{\"artist\":\"" + artist + "\",\"title\":\"" + title
                + "\",\"youtubeUrl\":\"https://youtu.be/dQw4w9WgXcQ\",\"comment\":null,\"listened\":" + listened + ",\"relisten\":false}";
    }

    private long firstId(String body) {
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

    private HttpResponse<String> patch(String path, String body) throws Exception {
        return request("PATCH", path, body);
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
            return Files.createTempDirectory("csc-x-tool-ballot-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
