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
    void exposesTheCompleteSortedLocalCountryCatalogWithoutCreatingParticipants() throws Exception {
        HttpResponse<String> countries = get("/api/countries");

        assertThat(countries.statusCode()).isEqualTo(200);
        assertThat(countries.body()).contains("\"code\":\"DE\",\"name\":\"Deutschland\"", "\"code\":\"AT\",\"name\":\"Österreich\"");
        assertThat(occurrences(countries.body(), "\"code\":" )).isEqualTo(249);
        assertThat(countries.body().indexOf("\"name\":\"Afghanistan\""))
                .isLessThan(countries.body().indexOf("\"name\":\"Deutschland\""));
        assertThat(get("/api/participants").body()).isEqualTo("[]");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Integer.class)).isZero();
    }

    @Test
    void managesParticipantAggregatesAliasesSearchAndActiveFilteringPersistently() throws Exception {
        long activeParticipantId = create("""
                {"displayName":"  Alex  ","countryCode":" de ","aliases":["  Alex Alt  ","Lex"],"active":true}
                """);
        long inactiveParticipantId = create("""
                {"displayName":"Mira","countryCode":"AT","aliases":["Maus"],"active":false}
                """);

        HttpResponse<String> defaults = get("/api/participants");
        assertThat(defaults.body()).contains("\"id\":" + activeParticipantId, "\"displayName\":\"Alex\"", "\"countryCode\":\"DE\"", "\"countryName\":\"Deutschland\"", "\"aliases\":[\"Alex Alt\",\"Lex\"]");
        assertThat(defaults.body()).doesNotContain("\"id\":" + inactiveParticipantId);
        assertThat(get("/api/participants?includeInactive=true").body()).contains("\"id\":" + inactiveParticipantId, "\"active\":false");
        assertThat(get("/api/participants?q=maus&includeInactive=true").body()).contains("\"id\":" + inactiveParticipantId);
        assertThat(get("/api/participants/" + inactiveParticipantId).body()).contains("\"displayName\":\"Mira\"", "\"active\":false");

        HttpResponse<String> unchangedAliases = patch(activeParticipantId, """
                {"displayName":"Alex Bearbeitet","countryCode":"DE","active":true}
                """);
        assertThat(unchangedAliases.statusCode()).isEqualTo(200);
        assertThat(unchangedAliases.body()).contains("\"aliases\":[\"Alex Alt\",\"Lex\"]");

        HttpResponse<String> updated = patch(activeParticipantId, """
                {"displayName":"Alex Bearbeitet","countryCode":"ch","active":false,"aliases":["Neu","Umbenannt"]}
                """);
        assertThat(updated.statusCode()).isEqualTo(200);
        assertThat(updated.body()).contains("\"countryCode\":\"CH\"", "\"countryName\":\"Schweiz\"", "\"active\":false", "\"aliases\":[\"Neu\",\"Umbenannt\"]");

        HttpResponse<String> duplicateAlias = patch(activeParticipantId, """
                {"displayName":"Darf nicht persistieren","countryCode":"DE","active":true,"aliases":["gleich","GLEICH"]}
                """);
        assertThat(duplicateAlias.statusCode()).isEqualTo(400);
        assertThat(duplicateAlias.body()).contains("\"code\":\"DUPLICATE_PARTICIPANT_ALIAS\"");
        assertThat(get("/api/participants/" + activeParticipantId).body()).contains("\"displayName\":\"Alex Bearbeitet\"", "\"aliases\":[\"Neu\",\"Umbenannt\"]");

        HttpResponse<String> invalidCountry = post("""
                {"displayName":"Ungültig","countryCode":"ZZ","active":true,"aliases":[]}
                """);
        assertThat(invalidCountry.statusCode()).isEqualTo(400);
        assertThat(invalidCountry.body()).contains("\"code\":\"INVALID_COUNTRY_CODE\"");
        assertThat(post("{\"displayName\":\" \",\"countryCode\":\"DE\",\"active\":true,\"aliases\":[]}").body())
                .contains("\"code\":\"VALIDATION_ERROR\"");

        assertThat(delete(activeParticipantId).statusCode()).isEqualTo(204);
        assertThat(get("/api/participants/" + activeParticipantId).statusCode()).isEqualTo(404);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant_alias WHERE participant_id = ?", Integer.class, activeParticipantId)).isZero();

        DataSource reopenedDataSource = SqliteDataSourceFactory.create(STORAGE_ROOT.resolve("data/csc-x-tool.db"));
        JdbcTemplate reopenedJdbcTemplate = new JdbcTemplate(reopenedDataSource);
        assertThat(reopenedJdbcTemplate.queryForObject("SELECT display_name FROM participant WHERE id = ?", String.class, inactiveParticipantId))
                .isEqualTo("Mira");
        assertThat(reopenedJdbcTemplate.queryForObject("SELECT alias FROM participant_alias WHERE participant_id = ?", String.class, inactiveParticipantId))
                .isEqualTo("Maus");
    }

    @Test
    void validatesParticipantInputsAndKeepsDatabaseRulesActive() throws Exception {
        assertThat(post("{\"displayName\":\"Alex\",\"countryCode\":\"DE\",\"aliases\":[\" \"]}").body())
                .contains("\"code\":\"VALIDATION_ERROR\"");
        assertThat(post("{\"displayName\":\"Alex\",\"countryCode\":\"DE\",\"aliases\":[\"Alias\",\"alias\"]}").body())
                .contains("\"code\":\"DUPLICATE_PARTICIPANT_ALIAS\"");
        assertThat(get("/api/participants?includeInactive=maybe").body()).contains("\"code\":\"INVALID_INCLUDE_INACTIVE\"");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Direkt', 'DE', 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
    }

    private long create(String body) throws Exception {
        HttpResponse<String> response = post(body);
        assertThat(response.statusCode()).isEqualTo(201);
        return firstId(response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return request("GET", path, null);
    }

    private HttpResponse<String> post(String body) throws Exception {
        return request("POST", "/api/participants", body);
    }

    private HttpResponse<String> patch(long participantId, String body) throws Exception {
        return request("PATCH", "/api/participants/" + participantId, body);
    }

    private HttpResponse<String> delete(long participantId) throws Exception {
        return request("DELETE", "/api/participants/" + participantId, null);
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

    private static int occurrences(String text, String needle) {
        int count = 0;
        int start = 0;
        while ((start = text.indexOf(needle, start)) >= 0) {
            count += 1;
            start += needle.length();
        }
        return count;
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-participant-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
