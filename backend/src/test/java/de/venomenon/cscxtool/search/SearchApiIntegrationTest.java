package de.venomenon.cscxtool.search;

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
class SearchApiIntegrationTest {

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
    void searchesBothSongTypesEscapesLikeWildcardsAndLimitsEachType() throws Exception {
        for (int index = 1; index <= 30; index++) {
            jdbcTemplate.update("""
                    INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                    VALUES (1, ?, ?, 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, "Artist % " + index, "Needle_" + index, index);
            jdbcTemplate.update("""
                    INSERT INTO contest_entry (motto_show_id, contest_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, created_at, updated_at)
                    VALUES (1, 1, ?, ?, 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, "Entry % " + index, "Needle_" + index, index);
        }
        jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (1, 'Wildcard check', 'NeedleX', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', 31, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        HttpResponse<String> results = get("/api/search?q=needle_");
        assertThat(results.statusCode()).isEqualTo(200);
        assertThat(results.body()).contains("\"type\":\"CANDIDATE\"", "\"type\":\"ENTRY\"");
        assertThat(results.body().split("\"type\":").length - 1).isEqualTo(50);
        assertThat(results.body()).doesNotContain("NeedleX");

        assertThat(get("/api/search?q=nothing%25").body()).isEqualTo("[]");
        assertThat(get("/api/search?q=%20%20%20").body()).isEqualTo("[]");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-search-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
