package de.venomenon.cscxtool;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductionArtifactSmokeTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @Test
    void servesTheSpaAndTheHealthApiFromOneOrigin() throws Exception {
        HttpResponse<String> home = get("/");
        HttpResponse<String> health = get("/api/system/health");

        assertThat(home.statusCode()).isEqualTo(200);
        assertThat(home.body()).contains("<div id=\"root\">");
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(health.body()).contains("\"status\":\"UP\"");
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
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
