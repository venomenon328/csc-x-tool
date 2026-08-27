package de.venomenon.cscxtool.show;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MottoShowApiIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void listsSeededShowsAndPersistsARenamedTba() throws Exception {
        HttpResponse<String> overview = get("/api/shows");

        assertThat(overview.statusCode()).isEqualTo(200);
        assertThat(overview.body()).contains("\"showNumber\":1", "\"showNumber\":12", "\"name\":\"TBA\"");

        HttpResponse<String> renamed = patch("/api/shows/9", "{\"name\":\"Neue Show neun\"}");
        HttpResponse<String> reread = get("/api/shows");

        assertThat(renamed.statusCode()).isEqualTo(200);
        assertThat(renamed.body()).contains("\"name\":\"Neue Show neun\"");
        assertThat(reread.body()).contains("\"name\":\"Neue Show neun\"");
    }

    @Test
    void returnsStructuredErrorsForBlankNamesAndUnknownShows() throws Exception {
        HttpResponse<String> blankName = patch("/api/shows/1", "{\"name\":\"   \"}");
        HttpResponse<String> unknownShow = patch("/api/shows/999", "{\"name\":\"Existiert nicht\"}");

        assertThat(blankName.statusCode()).isEqualTo(400);
        assertThat(blankName.body())
                .contains("\"code\":\"VALIDATION_ERROR\"", "Der Show-Name darf nicht leer sein.");
        assertThat(unknownShow.statusCode()).isEqualTo(404);
        assertThat(unknownShow.body())
                .contains("\"code\":\"SHOW_NOT_FOUND\"", "Die angeforderte Mottoshow wurde nicht gefunden.");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> patch(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .header("Content-Type", "application/json")
                        .method("PATCH", HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
