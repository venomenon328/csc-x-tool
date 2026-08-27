package de.venomenon.cscxtool;

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
class ProductionArtifactSmokeTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void servesTheSpaAndTheHealthApiFromOneOrigin() throws Exception {
        HttpResponse<String> home = get("/");
        HttpResponse<String> health = get("/api/system/health");
        HttpResponse<String> shows = get("/api/shows");
        HttpResponse<String> countries = get("/api/countries");
        HttpResponse<String> participants = get("/api/participants");

        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("<div id=\"root\">");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
        assertThat(shows.statusCode()).isEqualTo(200);
        assertThat(shows.body()).contains("\"showNumber\":1", "\"showNumber\":12");
        assertThat(countries.statusCode()).isEqualTo(200);
        assertThat(countries.body()).contains("\"code\":\"DE\"", "\"name\":\"Deutschland\"");
        assertThat(participants.statusCode()).isEqualTo(200);
        assertThat(participants.body()).isEqualTo("[]");
    }

    @Test
    void forwardsEachPlannedSpaRouteButNeverAnApiPath() throws Exception {
        String[] spaRoutes = {
                "/participants",
                "/data",
                "/shows/bootstrap/candidates",
                "/shows/bootstrap/voting",
                "/shows/bootstrap/result"
        };

        for (String route : spaRoutes) {
            HttpResponse<String> response = get(route);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("<div id=\"root\">");
        }

        HttpResponse<String> missingApi = get("/api/not-a-route");
        assertThat(missingApi.statusCode()).isEqualTo(404);
        assertThat(missingApi.body()).doesNotContain("<div id=\"root\">");
        assertThat(missingApi.body()).contains("\"status\":404");
        assertThat(missingApi.body()).contains("\"code\":\"RESOURCE_NOT_FOUND\"");
        assertThat(missingApi.body()).contains("\"message\":\"Die angeforderte Ressource wurde nicht gefunden.\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-artifact-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres Storage Root für den Artifact-Smoke-Test konnte nicht angelegt werden.", exception);
        }
    }
}
