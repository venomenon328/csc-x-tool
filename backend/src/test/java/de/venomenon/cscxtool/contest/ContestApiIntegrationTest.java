package de.venomenon.cscxtool.contest;

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
class ContestApiIntegrationTest {

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
    void createsRenamesAndConsciouslySwitchesTheCurrentContest() throws Exception {
        assertThat(get("/api/contests/current").body()).contains("\"id\":1", "\"name\":\"CSC X\"", "\"current\":true");

        HttpResponse<String> created = post("/api/contests", "{\"name\":\"  CSC IX  \"}");
        assertThat(created.statusCode()).isEqualTo(201);
        long contestId = firstId(created.body());
        assertThat(created.body()).contains("\"name\":\"CSC IX\"", "\"current\":false");

        assertThat(patch("/api/contests/" + contestId, "{\"name\":\"CSC VIII\"}").body()).contains("\"name\":\"CSC VIII\"");
        assertThat(post("/api/contests", "{\"name\":\"csc viii\"}").statusCode()).isEqualTo(409);

        HttpResponse<String> current = post("/api/contests/" + contestId + "/make-current", "");
        assertThat(current.statusCode()).isEqualTo(200);
        assertThat(current.body()).contains("\"id\":" + contestId, "\"current\":true");
        assertThat(get("/api/contests/current").body()).contains("\"id\":" + contestId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM contest WHERE is_current = 1", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest SET is_current = 1 WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
    private HttpResponse<String> patch(String path, String body) throws Exception { return request("PATCH", path, body); }

    private HttpResponse<String> request(String method, String path, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static long firstId(String body) {
        int start = body.indexOf("\"id\":") + 5;
        int end = body.indexOf(',', start);
        return Long.parseLong(body.substring(start, end));
    }

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-contest-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception); }
    }
}
