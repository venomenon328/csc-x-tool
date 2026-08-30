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

    @Test
    void selectsOnlyAnExplicitParticipationFromTheSameContestAndConfirmsDerivedResultChanges() throws Exception {
        jdbcTemplate.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (10,'Ich',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (11,'Andere',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (10,1,10,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbcTemplate.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (11,1,11,'AT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        long otherContestId = firstId(post("/api/contests", "{\"name\":\"CSC Fremd\"}").body());
        jdbcTemplate.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (12,?,11,'AT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", otherContestId);

        HttpResponse<String> selected = put("/api/contests/1/own-participation", "{\"participationId\":10}");
        assertThat(selected.statusCode()).isEqualTo(200);
        assertThat(selected.body()).contains("\"ownParticipationId\":10");
        assertThat(put("/api/contests/1/own-participation", "{\"participationId\":12}").statusCode()).isEqualTo(409);

        jdbcTemplate.update("UPDATE motto_show SET entry_list_complete = 1 WHERE id = 1");
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                VALUES (1,1,'Eigene Band','Eigener Song','https://example.test/eigen',1,10,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        HttpResponse<String> unconfirmed = put("/api/contests/1/own-participation", "{\"participationId\":11}");
        assertThat(unconfirmed.statusCode()).isEqualTo(409);
        assertThat(unconfirmed.body()).contains("OWN_PARTICIPATION_CHANGE_CONFIRMATION_REQUIRED");
        assertThat(put("/api/contests/1/own-participation", "{\"participationId\":11,\"confirmChange\":true}").body())
                .contains("\"ownParticipationId\":11");
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
    private HttpResponse<String> patch(String path, String body) throws Exception { return request("PATCH", path, body); }
    private HttpResponse<String> put(String path, String body) throws Exception { return request("PUT", path, body); }

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
