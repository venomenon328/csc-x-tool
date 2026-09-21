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
class CurrentAssignmentImportApiIntegrationTest {

    private static final Path STORAGE_ROOT = storageRoot();
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void previewsRealFormatsAndCommitsOnlyAtomicAssignmentsToExistingEntries() throws Exception {
        fixture();
        String html = "<script>alert('untrusted')</script><p><strong>Frankreich - Clara </strong>"
                + "<a href=\"https://youtu.be/ccccccccccc\">Band C - Song C</a></p>";
        String text = "Band A - Song A (Deutschland/Alicia)\nBand B - Song B - Bob / Schweiz\n"
                + "**Frankreich - Clara **[Band C - Song C](https://youtu.be/ccccccccccc)";
        HttpResponse<String> preview = post("/assignment-import-preview", "{\"html\":\"" + json(html) + "\",\"text\":\"" + json(text) + "\"}");
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.body()).contains("\"entryId\":9721", "\"entryId\":9722", "\"entryId\":9723",
                "\"participantId\":9711", "\"participantId\":9712", "\"participantId\":9713",
                "\"action\":\"NEW\"", "https://youtu.be/ccccccccccc");
        assertThat(count(preview.body(), "\"sourcePosition\"")).isEqualTo(3);
        assertThat(preview.body()).doesNotContain("untrusted", "<script>");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = 9710", Integer.class)).isEqualTo(5);
        assertThat(assignment(9721)).isNull();

        HttpResponse<String> urlOnly = post("/assignment-import-preview", "{\"text\":\"**Frankreich - Clara **[Wrong Band - Wrong Song](https://youtu.be/ccccccccccc)\"}");
        assertThat(urlOnly.body()).contains("\"entryId\":9723");
        HttpResponse<String> conflictingSignals = post("/assignment-import-preview", "{\"text\":\"**Frankreich - Clara **[Band B - Song B](https://youtu.be/aaaaaaaaaaa)\"}");
        assertThat(conflictingSignals.body()).contains("ENTRY_SIGNAL_CONFLICT", "\"entryId\":null");
        HttpResponse<String> missingSong = post("/assignment-import-preview", "{\"text\":\"Unknown - New (Deutschland/Alice)\"}");
        assertThat(missingSong.body()).contains("ENTRY_NOT_FOUND", "\"sourceText\":\"Unknown - New (Deutschland/Alice)\"");
        HttpResponse<String> countryConflict = post("/assignment-import-preview", "{\"text\":\"Band A - Song A (Schweiz/Alicia)\"}");
        assertThat(countryConflict.body()).contains("COUNTRY_CONFLICT", "\"participantId\":9711");

        assertThat(post("/assignment-import", batch(item(9721, 9711, null, false), item(9722, 9712, null, false))).statusCode()).isEqualTo(200);
        assertThat(assignment(9721)).isEqualTo(9711);
        assertThat(assignment(9722)).isEqualTo(9712);
        assertThat(assignment(9723)).isNull();
        assertThat(post("/assignment-import", batch(item(9721, 9711, 9711L, false))).statusCode()).isEqualTo(200);

        HttpResponse<String> replacementWithoutConfirmation = post("/assignment-import", batch(item(9721, 9712, 9711L, false)));
        assertThat(replacementWithoutConfirmation.statusCode()).isEqualTo(409);
        assertThat(replacementWithoutConfirmation.body()).contains("ASSIGNMENT_REPLACEMENT_CONFIRMATION_REQUIRED");
        assertThat(post("/assignment-import", batch(item(9721, 9712, 9711L, true), item(9722, 9711, 9712L, true))).statusCode()).isEqualTo(200);
        assertThat(assignment(9721)).isEqualTo(9712);
        assertThat(assignment(9722)).isEqualTo(9711);

        HttpResponse<String> stale = post("/assignment-import", batch(item(9721, 9711, 9711L, true)));
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("ASSIGNMENT_IMPORT_STALE");
        assertThat(post("/assignment-import", batch(item(9721, 9711, 9712L, true), item(9723, 99999, null, false))).statusCode()).isEqualTo(409);
        assertThat(assignment(9721)).isEqualTo(9712);
        assertThat(assignment(9723)).isNull();
        assertThat(post("/assignment-import", batch(item(9723, 9713, null, false), item(9724, 9713, null, false))).statusCode()).isEqualTo(409);
        assertThat(assignment(9723)).isNull();
        assertThat(post("/assignment-import", batch(item(9723, 9713, null, false), item(9723, 9712, null, false))).statusCode()).isEqualTo(400);
        assertThat(post("/assignment-import", batch(item(9720, 9710, 9710L, false))).statusCode()).isEqualTo(200);
        assertThat(post("/assignment-import", batch(item(9720, 9711, 9710L, true))).statusCode()).isEqualTo(409);
        assertThat(post("/assignment-import", batch(item(9723, 9710, null, false))).statusCode()).isEqualTo(409);
        assertThat(post("/assignment-import", batch(item(9723, 9714, null, false))).statusCode()).isEqualTo(409);
        assertThat(post("/assignment-import", batch(item(9723, 9760, null, false))).statusCode()).isEqualTo(409);

        jdbc.update("INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) "
                + "VALUES (9750,9710,1,9715,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO published_ballot_position (id,published_ballot_id,contest_entry_id,rank) VALUES (9751,9750,9724,1)");
        HttpResponse<String> ballotConflict = post("/assignment-import", batch(item(9724, 9715, null, false)));
        assertThat(ballotConflict.statusCode()).isEqualTo(409);
        assertThat(ballotConflict.body()).contains("PUBLISHED_BALLOT_OWN_ENTRY_CONFLICT");
        assertThat(assignment(9724)).isNull();

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = 9710", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT artist FROM contest_entry WHERE id = 9721", String.class)).isEqualTo("Band A");
        assertThat(jdbc.queryForObject("SELECT youtube_url FROM contest_entry WHERE id = 9721", String.class)).isEqualTo("https://youtu.be/aaaaaaaaaaa");

        jdbc.update("INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,created_at,updated_at) "
                + "VALUES (9725,9710,1,'Band A','Song A','https://youtu.be/ffffffffffF',6,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        HttpResponse<String> duplicateTitle = post("/assignment-import-preview", "{\"text\":\"Band A - Song A (Deutschland/Alicia)\"}");
        assertThat(duplicateTitle.body()).contains("AMBIGUOUS_ENTRY", "\"entryId\":null");
    }

    private void fixture() {
        for (int id = 9710; id <= 9715; id++) {
            jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                    id, switch (id) { case 9710 -> "Eigene Person"; case 9711 -> "Alice"; case 9712 -> "Bob";
                        case 9713 -> "Clara"; case 9714 -> "Inaktiv"; default -> "Dave"; });
            jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) "
                    + "VALUES (?,1,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, id,
                    switch (id) { case 9712 -> "CH"; case 9713 -> "FR"; default -> "DE"; }, id == 9714 ? 0 : 1);
        }
        jdbc.update("INSERT INTO participant_alias (participant_id,alias) VALUES (9711,'Alicia')");
        jdbc.update("INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at) "
                + "VALUES (9710,'Andere Ausgabe',9710,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) "
                + "VALUES (9760,9710,9711,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("UPDATE contest SET own_participation_id = 9710 WHERE id = 1");
        jdbc.update("INSERT INTO motto_show (id,contest_id,show_number,name,created_at,updated_at) "
                + "VALUES (9710,1,9710,'Importtest',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int index = 0; index < 5; index++) {
            long id = 9720 + index;
            jdbc.update("INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at) "
                    + "VALUES (?,9710,1,?,?,?, ?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id,
                    index == 0 ? "Eigene Band" : "Band " + (char) ('A' + index - 1),
                    index == 0 ? "Eigener Song" : "Song " + (char) ('A' + index - 1),
                    "https://youtu.be/" + String.valueOf((char) ('a' + index)).repeat(11), index + 1, index == 0 ? 9710L : null);
        }
        jdbc.update("UPDATE motto_show SET own_entry_resolution = 'OWN_ENTRY', own_entry_participation_id = 9710, "
                + "own_entry_id = 9720, ballot_closed_at = CURRENT_TIMESTAMP WHERE id = 9710");
    }

    private Long assignment(long entryId) {
        return jdbc.queryForObject("SELECT contest_participation_id FROM contest_entry WHERE id = ?", Long.class, entryId);
    }

    private HttpResponse<String> post(String suffix, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/shows/9710/entries" + suffix))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String item(long entry, long participation, Long expected, boolean replacement) {
        return "{\"entryId\":" + entry + ",\"participationId\":" + participation + ",\"expectedParticipationId\":"
                + expected + ",\"confirmReplacement\":" + replacement + "}";
    }
    private static String batch(String... items) { return "{\"assignments\":[" + String.join(",", items) + "]}"; }
    private static String json(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n"); }
    private static int count(String haystack, String needle) { return (haystack.length() - haystack.replace(needle, "").length()) / needle.length(); }
    private static Path storageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-current-assignment-api-"); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
