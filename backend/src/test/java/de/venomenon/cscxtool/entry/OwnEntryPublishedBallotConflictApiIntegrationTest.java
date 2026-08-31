package de.venomenon.cscxtool.entry;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OwnEntryPublishedBallotConflictApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void rejectsOwnEntryResolutionThatWouldInvalidateAnExistingPublishedBallotBeforeMutation() throws Exception {
        long showId = 9200;
        long participationId = 9200;
        jdbc.update("""
                INSERT INTO participant (id,display_name,active,created_at,updated_at)
                VALUES (9200,'Eigener Published-Ballot-Test',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at)
                VALUES (9200,1,9200,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("UPDATE contest SET own_participation_id = ? WHERE id = 1", participationId);
        jdbc.update("""
                INSERT INTO motto_show (id,contest_id,show_number,name,created_at,updated_at)
                VALUES (9200,1,9200,'Published-Ballot-Konflikt',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        for (int rank = 1; rank <= 15; rank++) {
            long entryId = 9200L + rank;
            jdbc.update("""
                    INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,created_at,updated_at)
                    VALUES (?,9200,1,?,?,?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, entryId, "Band " + rank, "Song " + rank, "https://example.test/" + rank, rank);
        }
        jdbc.update("""
                INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at)
                VALUES (9200,9200,1,9200,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("""
                    INSERT INTO published_ballot_position (id,published_ballot_id,contest_entry_id,rank)
                    VALUES (?,9200,?,?)
                    """, 9300 + rank, 9200 + rank, rank);
        }

        long targetEntryId = 9201;
        HttpResponse<String> response = put(
                "/api/shows/" + showId + "/entries/own-entry-resolution",
                "{\"resolution\":\"OWN_ENTRY\",\"entryId\":" + targetEntryId + ",\"confirmRankingRemoval\":false}"
        );

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("PUBLISHED_BALLOT_OWN_ENTRY_CONFLICT", "veröffentlichten Stimmzettel");
        assertThat(jdbc.queryForObject(
                "SELECT contest_participation_id FROM contest_entry WHERE id = ?", Long.class, targetEntryId
        )).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT own_entry_resolution FROM motto_show WHERE id = ?", String.class, showId
        )).isEqualTo("UNRESOLVED");
    }

    private HttpResponse<String> put(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-own-entry-published-ballot-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht erstellt werden.", exception); }
    }
}
