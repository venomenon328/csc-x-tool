package de.venomenon.cscxtool;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.data.BackupReason;
import de.venomenon.cscxtool.data.BackupService;
import de.venomenon.cscxtool.data.BackupSummary;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Exercises the public native restore route and then starts a new application process context. */
class RestoreRestartEndToEndTest {

    @TempDir
    Path temporaryDirectory;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void existingEmptySqliteFileGetsTheStartupBackupButNoPreMigrationBackup() throws Exception {
        Path storageRoot = temporaryDirectory.resolve("empty-storage");
        Path databaseFile = storageRoot.resolve("data/csc-x-tool.db");
        Files.createDirectories(databaseFile.getParent());
        Files.createFile(databaseFile);

        try (ConfigurableApplicationContext context = start(storageRoot)) {
            BackupService backups = context.getBean(BackupService.class);
            assertThat(backups.overview().automaticBackups())
                    .extracting(BackupSummary::reason)
                    .containsExactly(BackupReason.STARTUP);
        }
    }

    @Test
    void restoredDataSurvivesANewApplicationStartup() throws Exception {
        Path storageRoot = temporaryDirectory.resolve("storage");
        try (ConfigurableApplicationContext first = start(storageRoot)) {
            JdbcTemplate jdbc = new JdbcTemplate(first.getBean(DataSource.class));
            jdbc.update("""
                    INSERT INTO candidate (motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at)
                    VALUES (1,'Restart','Historischer Titel','https://www.youtube.com/watch?v=dQw4w9WgXcQ','Vor Restore','OFFEN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """);
            URI base = base(first);
            String backupId = jsonValue(post(base.resolve("/api/data/backups"), "").body(), "id");
            jdbc.update("UPDATE candidate SET comment = 'Nach Restore' WHERE motto_show_id = 1");
            String token = jsonValue(post(base.resolve("/api/data/restore/preview/backups/" + backupId), "").body(), "token");

            HttpResponse<String> restore = post(base.resolve("/api/data/restore"), "{\"token\":\"" + token + "\"}");
            assertThat(restore.statusCode()).isEqualTo(200);
            assertThat(get(base.resolve("/api/shows/1/candidates")).body()).contains("Vor Restore");
        }

        try (ConfigurableApplicationContext restarted = start(storageRoot)) {
            URI base = base(restarted);
            assertThat(get(base.resolve("/api/system/health")).statusCode()).isEqualTo(200);
            assertThat(get(base.resolve("/api/shows/1/candidates")).body()).contains("Vor Restore").doesNotContain("Nach Restore");
        }
    }

    private ConfigurableApplicationContext start(Path storageRoot) {
        return new SpringApplicationBuilder(CscXToolApplication.class)
                .properties("server.port=0", "csc-x-tool.storage.root=" + storageRoot.toAbsolutePath())
                .run();
    }

    private static URI base(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return URI.create("http://127.0.0.1:" + port);
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(URI uri, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonValue(String json, String field) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\":\\\"([^\\\"]+)\\\"").matcher(json);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
