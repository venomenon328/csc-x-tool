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
class HistoricalContestEntriesApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void keepsHistoricalSongListsCompleteAtomicAndSeparateFromParticipantMasterData() throws Exception {
        long contestId = firstId(post("/api/contests", "{\"name\":\"CSC IX\"}").body());
        long alice = firstId(post("/api/participants", "{\"displayName\":\"Alice\",\"active\":true}").body());
        long bob = firstId(post("/api/participants", "{\"displayName\":\"Bob\",\"active\":true}").body());
        assertThat(post("/api/contests/" + contestId + "/participants", "{\"participantId\":" + alice + ",\"countryCode\":\"DE\",\"active\":true}").statusCode()).isEqualTo(201);
        assertThat(post("/api/contests/" + contestId + "/participants", "{\"participantId\":" + bob + ",\"countryCode\":\"XS\",\"active\":true}").statusCode()).isEqualTo(201);
        long showId = firstId(post("/api/contests/" + contestId + "/shows", "{\"showNumber\":3,\"name\":\"Archivthema\"}").body());

        int participantsBeforePreview = jdbc.queryForObject("SELECT COUNT(*) FROM participant", Integer.class);
        HttpResponse<String> preview = post("/api/shows/" + showId + "/entries/historical-import-preview", """
                {"html":"","text":"Artist One - First Song (Deutschland/Alice)\\nArtist Two - Second Song (Bob/Schottland)\\nUnknown - Song (Finnland/Nicht gepflegt)"}
                """);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"participantDisplayName\":\"Alice\"", "\"participantDisplayName\":\"Bob\"", "UNRESOLVED_PARTICIPANT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant", Integer.class)).isEqualTo(participantsBeforePreview);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = ?", Integer.class, showId)).isZero();

        HttpResponse<String> invalidImport = post("/api/shows/" + showId + "/entries/historical-import", """
                {"entries":[
                  {"artist":"Artist One","title":"First Song","youtubeUrl":null,"participantId":%d},
                  {"artist":"Broken","title":"Song","youtubeUrl":null,"participantId":99999}
                ]}
                """.formatted(alice));
        assertThat(invalidImport.statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = ?", Integer.class, showId)).isZero();

        HttpResponse<String> imported = post("/api/shows/" + showId + "/entries/historical-import", """
                {"entries":[
                  {"artist":"Artist One","title":"First Song","youtubeUrl":null,"participantId":%d},
                  {"artist":"Artist Two","title":"Second Song","youtubeUrl":"https://source.example/song","participantId":%d}
                ]}
                """.formatted(alice, bob));
        assertThat(imported.statusCode()).isEqualTo(200);
        assertThat(imported.body()).contains("\"youtubeUrl\":null", "https://source.example/song", "\"participantId\":" + alice);

        assertThat(post("/api/shows/" + showId + "/entries/entry-list/complete", "").statusCode()).isEqualTo(204);
        assertThat(get("/api/shows/" + showId).body()).contains("\"entryListComplete\":true");
        assertThat(post("/api/shows/" + showId + "/entries", "{\"artist\":\"Late\",\"title\":\"Entry\",\"youtubeUrl\":null,\"participantId\":" + alice + "}").statusCode()).isEqualTo(409);
        assertThat(post("/api/shows/" + showId + "/entries/entry-list/reopen", "").statusCode()).isEqualTo(204);
        assertThat(get("/api/shows/" + showId).body()).contains("\"entryListComplete\":false");
    }

    private HttpResponse<String> get(String path) throws Exception { return request("GET", path, null); }
    private HttpResponse<String> post(String path, String body) throws Exception { return request("POST", path, body); }
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
        try { return Files.createTempDirectory("csc-x-tool-historical-entry-api-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden,", exception); }
    }
}
