package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "csc-x-tool.security.csrf-enabled=true"
)
class SystemSecurityIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private final HttpClient client = HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void protectsWritesWithCsrfAndRejectsForeignOriginsWhileKeepingReadsAvailable() throws Exception {
        assertThat(request("GET", "/api/system/health", null, null, null).statusCode()).isEqualTo(200);

        HttpResponse<String> rejected = request("POST", "/api/shows/1/candidates", candidateJson(), null, null);
        assertThat(rejected.statusCode()).isEqualTo(403);
        assertThat(rejected.body()).contains("\"code\":\"CSRF_REJECTED\"");

        HttpResponse<String> csrf = request("GET", "/api/system/csrf", null, null, null);
        String token = jsonValue(csrf.body(), "token");
        String headerName = jsonValue(csrf.body(), "headerName");

        HttpResponse<String> accepted = request("POST", "/api/shows/1/candidates", candidateJson(), headerName, token);
        assertThat(accepted.statusCode()).isEqualTo(201);

        HttpResponse<String> foreignOrigin = request(
                "POST", "/api/shows/1/candidates", candidateJson(), headerName, token,
                "https://example.invalid"
        );
        assertThat(foreignOrigin.statusCode()).isEqualTo(403);
        assertThat(foreignOrigin.body()).contains("\"code\":\"LOCAL_ORIGIN_REQUIRED\"");
    }

    private HttpResponse<String> request(String method, String path, String body, String headerName, String token) throws Exception {
        return request(method, path, body, headerName, token, null);
    }

    private HttpResponse<String> request(
            String method, String path, String body, String headerName, String token, String origin
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (body != null) request.header("Content-Type", "application/json");
        if (headerName != null) request.header(headerName, token);
        if (origin != null) request.header("Origin", origin);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String candidateJson() {
        return "{\"artist\":\"CSRF Artist\",\"title\":\"CSRF Titel\",\"youtubeUrl\":\"https://youtu.be/dQw4w9WgXcQ\",\"comment\":null}";
    }

    private static String jsonValue(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-security-api-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
