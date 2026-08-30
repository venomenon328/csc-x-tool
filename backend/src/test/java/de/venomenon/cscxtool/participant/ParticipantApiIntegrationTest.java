package de.venomenon.cscxtool.participant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ParticipantApiIntegrationTest {

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
    void keepsIdentityAliasesAndContestParticipationCountriesSeparated() throws Exception {
        long participantId = firstId(post("/api/participants", """
                {"displayName":"  Alex  ","active":true,"aliases":["  Alex Alt  ","Lex"]}
                """).body());
        long archiveId = firstId(post("/api/contests", "{" + "\"name\":\"Archiv " + participantId + "\"}" ).body());

        assertThat(post("/api/contests/1/participants", """
                {"participantId":%d,"countryCode":"de","active":true}
                """.formatted(participantId)).statusCode()).isEqualTo(201);
        assertThat(post("/api/contests/" + archiveId + "/participants", """
                {"participantId":%d,"countryCode":"at","active":false}
                """.formatted(participantId)).statusCode()).isEqualTo(201);

        assertThat(get("/api/contests/1/participants").body()).contains(
                "\"id\":" + participantId, "\"displayName\":\"Alex\"", "\"countryCode\":\"DE\"", "\"aliases\":[\"Alex Alt\",\"Lex\"]"
        );
        assertThat(get("/api/contests/" + archiveId + "/participants").body()).isEqualTo("[]");
        assertThat(get("/api/contests/" + archiveId + "/participants?includeInactive=true").body()).contains("\"countryCode\":\"AT\"", "\"active\":false");

        assertThat(patch("/api/participants/" + participantId, """
                {"displayName":"Alex Bearbeitet","active":true,"aliases":["Neu"]}
                """).statusCode()).isEqualTo(200);
        HttpResponse<String> updatedParticipation = patch("/api/contests/" + archiveId + "/participants/" + participantId,
                "{" + "\"countryCode\":\"ch\",\"active\":true}");
        assertThat(updatedParticipation.statusCode()).isEqualTo(200);
        assertThat(updatedParticipation.body()).contains("\"displayName\":\"Alex Bearbeitet\"", "\"countryCode\":\"CH\"", "\"aliases\":[\"Neu\"]");
        assertThat(get("/api/contests/1/participants").body()).contains("\"countryCode\":\"DE\"", "\"aliases\":[\"Neu\"]");

        assertThat(delete("/api/contests/" + archiveId + "/participants/" + participantId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/participants/" + participantId).statusCode()).isEqualTo(409);
        assertThat(delete("/api/contests/1/participants/" + participantId).statusCode()).isEqualTo(204);
        assertThat(delete("/api/participants/" + participantId).statusCode()).isEqualTo(204);
        assertThat(get("/api/participants/" + participantId).statusCode()).isEqualTo(404);

        DataSource reopened = SqliteDataSourceFactory.create(STORAGE_ROOT.resolve("data/csc-x-tool.db"));
        assertThat(new JdbcTemplate(reopened).queryForObject("SELECT COUNT(*) FROM pragma_table_info('participant') WHERE name = 'country_code'", Integer.class))
                .isZero();
    }

    @Test
    void createsANewIdentityAndItsFirstParticipationAtomically() throws Exception {
        HttpResponse<String> created = post("/api/contests/1/participants", """
                {"displayName":"  Neu  ","aliases":[" Alias "],"countryCode":"de","active":false}
                """);

        assertThat(created.statusCode()).isEqualTo(201);
        long participantId = firstId(created.body());
        assertThat(created.body()).contains("\"displayName\":\"Neu\"", "\"countryCode\":\"DE\"", "\"active\":false", "\"aliases\":[\"Alias\"]");
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM participant WHERE id = ?", Boolean.class, participantId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT active FROM contest_participation WHERE contest_id = 1 AND participant_id = ?", Boolean.class, participantId)).isFalse();

        int identitiesBeforeInvalidRequest = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Integer.class);
        assertThat(post("/api/contests/1/participants", """
                {"displayName":"Darf nicht bleiben","aliases":[],"countryCode":"XX","active":true}
                """).statusCode()).isEqualTo(400);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Integer.class)).isEqualTo(identitiesBeforeInvalidRequest);
    }

    @Test
    void exposesCountryCatalogAndValidatesParticipationAndIdentityInputs() throws Exception {
        HttpResponse<String> countries = get("/api/countries");
        assertThat(countries.statusCode()).isEqualTo(200);
        assertThat(countries.body()).contains("\"code\":\"DE\",\"name\":\"Deutschland\"");
        assertThat(post("/api/participants", "{\"displayName\":\" \",\"aliases\":[]}").body()).contains("VALIDATION_ERROR");
        assertThat(post("/api/contests/1/participants", "{\"participantId\":99999,\"countryCode\":\"DE\"}").body())
                .contains("PARTICIPANT_NOT_FOUND");
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO participant (display_name, active, created_at, updated_at)
                VALUES ('Direkt', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
    private HttpResponse<String> patch(String path, String body) throws Exception { return request("PATCH", path, body); }
    private HttpResponse<String> delete(String path) throws Exception { return request("DELETE", path, null); }

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
        try { return Files.createTempDirectory("csc-x-tool-participant-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception); }
    }
}
