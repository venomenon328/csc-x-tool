package de.venomenon.cscxtool.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ResultApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @BeforeEach
    void resetFixture() {
        jdbc.update("UPDATE contest SET own_participation_id = NULL WHERE id = 1");
        jdbc.update("DELETE FROM published_ballot_position WHERE published_ballot_id IN (SELECT id FROM published_ballot WHERE motto_show_id = 1)");
        jdbc.update("DELETE FROM published_ballot WHERE motto_show_id = 1");
        jdbc.update("DELETE FROM legacy_received_score WHERE motto_show_id = 1");
        jdbc.update("DELETE FROM ballot_snapshot_item WHERE ballot_snapshot_id IN (SELECT id FROM ballot_snapshot WHERE motto_show_id = 1)");
        jdbc.update("DELETE FROM ballot_snapshot WHERE motto_show_id = 1");
        jdbc.update("DELETE FROM contest_entry WHERE motto_show_id = 1");
        jdbc.update("DELETE FROM contest_participation WHERE contest_id = 1");
        jdbc.update("DELETE FROM participant WHERE id BETWEEN 1 AND 5");
        jdbc.update("UPDATE motto_show SET entry_list_complete = 0, ballot_closed_at = NULL WHERE id = 1");
    }

    @Test
    void derivesEveryOwnResultStateFromPublishedBallotsOnly() throws Exception {
        insertContestFixture();

        HttpResponse<String> response = get("/api/shows/1/results");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains(
                "\"prerequisite\":\"READY\"", "\"state\":\"OWN_ENTRY\"", "\"state\":\"RANKED\"",
                "\"rank\":1", "\"points\":25", "\"state\":\"OUTSIDE_TOP_15\"", "\"points\":0",
                "\"state\":\"NO_BALLOT\"", "\"state\":\"UNKNOWN\"", "\"derivedTotalPoints\":25"
        );
        assertThat(response.body()).doesNotContain("officialTotalPoints", "finalPlace", "resultsClosedAt");
    }

    @Test
    void leavesAnOldZeroScoreUnknownAndDoesNotCreateABallot() throws Exception {
        insertContestFixture();
        jdbc.update("""
                INSERT INTO legacy_received_score (id,motto_show_id,contest_id,contest_participation_id,status,points,created_at,updated_at,archived_at)
                VALUES (900,1,1,4,'ABGESTIMMT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);

        HttpResponse<String> response = get("/api/shows/1/results");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot WHERE contest_participation_id = 4", Integer.class)).isZero();
        assertThat(response.body()).contains("\"displayName\":\"Unbekannt\"", "\"state\":\"UNKNOWN\"");
        assertThat(get("/api/shows/1/results/legacy").body()).contains("\"status\":\"ABGESTIMMT\"", "\"points\":0");
        assertThat(response.body()).doesNotContain("\"displayName\":\"Unbekannt\",\"countryCode\":\"DK\",\"countryName\":\"Dänemark\",\"ballotStatus\":\"ABGESTIMMT\"");
    }

    @Test
    void reportsMissingOwnParticipationAndOwnEntryExplicitly() throws Exception {
        assertThat(get("/api/shows/1/results").body()).contains("\"prerequisite\":\"OWN_PARTICIPATION_MISSING\"");
        insertParticipant(1, "Ich", "DE");
        jdbc.update("UPDATE contest SET own_participation_id = 1 WHERE id = 1");
        jdbc.update("UPDATE motto_show SET entry_list_complete = 1 WHERE id = 1");
        assertThat(get("/api/shows/1/results").body()).contains("\"prerequisite\":\"OWN_ENTRY_MISSING\"");
    }

    @Test
    void requiresACompleteSongMappingBeforeDerivingOwnResults() throws Exception {
        insertParticipant(1, "Ich", "DE");
        jdbc.update("UPDATE contest SET own_participation_id = 1 WHERE id = 1");
        jdbc.update("""
                INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                VALUES (100,1,1,'Band','Song','https://example.test/100',1,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);

        assertThat(get("/api/shows/1/results").body()).contains("\"prerequisite\":\"ENTRY_LIST_INCOMPLETE\"");
    }

    private void insertContestFixture() {
        insertParticipant(1, "Ich", "DE");
        insertParticipant(2, "Rang", "AT");
        insertParticipant(3, "Keine Stimme", "BE");
        insertParticipant(4, "Unbekannt", "DK");
        insertParticipant(5, "Außerhalb", "CH");
        jdbc.update("UPDATE contest SET own_participation_id = 1 WHERE id = 1");
        jdbc.update("UPDATE motto_show SET entry_list_complete = 1 WHERE id = 1");
        for (int id = 100; id <= 115; id++) {
            Long participation = id == 100 ? Long.valueOf(1) : id == 101 ? Long.valueOf(2) : null;
            jdbc.update("""
                    INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                    VALUES (?,1,1,?,?,?, ?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, id, "Band " + id, "Song " + id, "https://example.test/" + id, id - 99, participation);
        }
        jdbc.update("""
                INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at)
                VALUES (200,1,1,2,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                       (201,1,1,3,'NICHT_ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP),
                       (202,1,1,5,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        for (int rank = 1; rank <= 15; rank++) {
            long entryId = rank == 1 ? 100 : 100 + rank;
            jdbc.update("INSERT INTO published_ballot_position (published_ballot_id,contest_entry_id,rank) VALUES (200,?,?)", entryId, rank);
        }
    }

    private void insertParticipant(long id, String name, String country) {
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, name);
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (?,1,?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, id, country);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-result-"); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
