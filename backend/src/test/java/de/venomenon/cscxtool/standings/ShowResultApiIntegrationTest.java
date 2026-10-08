package de.venomenon.cscxtool.standings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import de.venomenon.cscxtool.data.*;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ShowResultApiIntegrationTest {
    private static final Path ROOT = storage();
    private static final AtomicLong IDS = new AtomicLong(1000);
    private final HttpClient client = HttpClient.newHttpClient();
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired ExportService exports;
    @Autowired RestoreService restores;
    @Autowired BackupService backups;
    @Autowired ContestResultService service;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { registry.add("csc-x-tool.storage.root", () -> ROOT.toString()); }

    @Test
    void requiresCanonicalCompleteListsEveryParticipationAndAtLeastOneValidBallot() {
        long id = fixture();
        assertThat(closure(id).status()).isEqualTo("READY");
        assertThat(service.standings(id).rows()).hasSize(19).allSatisfy(row -> assertThat(row.rank()).isNull());
        jdbc.update("UPDATE contest_entry SET contest_participation_id = NULL WHERE id = ?", id + 18);
        assertThat(send("POST", closurePath(id) + "/close", "").body()).contains("SHOW_RESULT_NOT_READY", "Zuordnungen");
        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", id + 18, id + 18);
        jdbc.update("DELETE FROM published_ballot WHERE id = ?", id + 18); // Inactive participant is still required.
        assertThat(closure(id).reasons()).anyMatch(r -> r.contains("UNERFASST"));
        assertThat(send("PUT", ballotPath(id, id + 18), "{\"status\":\"NICHT_ABGESTIMMT\"}").statusCode()).isEqualTo(204);
        assertThat(closure(id).status()).isEqualTo("READY");
        assertThat(send("PUT", ballotPath(id, id + 19), "{\"status\":\"NICHT_ABGESTIMMT\"}").statusCode()).isEqualTo(204);
        assertThat(closure(id).reasons()).anyMatch(r -> r.contains("Mindestens ein"));
        assertThat(send("POST", closurePath(id) + "/close", "").statusCode()).isEqualTo(409);
        assertThat(service.standings(id).includedShowIds()).isEmpty();
    }

    @Test
    void guardsHistoricalMutationsAtomicallyAndAllowsDisplayChanges() {
        long id = fixture();
        assertThat(send("POST", closurePath(id) + "/close", "").statusCode()).isEqualTo(200);
        var before = service.standings(id);
        String timestamp = closure(id).closedAt();
        assertThat(send("POST", closurePath(id) + "/close", "").statusCode()).isEqualTo(200);
        assertThat(closure(id).closedAt()).isEqualTo(timestamp);
        List<HttpResponse<String>> conflicts = List.of(
                send("PUT", ballotPath(id, id + 19), "{\"status\":\"UNERFASST\"}"),
                send("POST", "/api/shows/" + id + "/published-ballots/import", ballot(id, false)),
                send("DELETE", "/api/shows/" + id + "/entries/" + (id + 18), null),
                send("POST", "/api/shows/" + id + "/entries", "{\"artist\":\"New\",\"title\":\"Song\",\"participantId\":" + (id + 19) + "}"),
                send("POST", "/api/shows/" + id + "/entries/historical-import", "{\"entries\":[]}"),
                send("POST", "/api/shows/" + id + "/entries/entry-list/reopen", ""),
                send("POST", "/api/contests/" + id + "/participants", "{\"displayName\":\"Must not leak\",\"countryCode\":\"DE\"}"),
                send("DELETE", "/api/contests/" + id + "/participants/" + (id + 19), null),
                send("PATCH", "/api/contests/" + id + "/shows/" + id, "{\"showNumber\":2,\"name\":\"Changed\"}"));
        assertThat(conflicts).allSatisfy(r -> { assertThat(r.statusCode()).isEqualTo(409); assertThat(r.body()).contains("SHOW_RESULT_CLOSED"); });
        assertThat(service.standings(id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM participant WHERE display_name = 'Must not leak'", Integer.class)).isZero();
        assertThat(send("PATCH", "/api/participants/" + (id + 1), "{\"displayName\":\"Renamed\",\"active\":false}").statusCode()).isEqualTo(200);
        assertThat(send("PATCH", "/api/contests/" + id + "/participants/" + (id + 1), "{\"countryCode\":\"AT\",\"active\":false}").statusCode()).isEqualTo(200);
        assertThat(send("PATCH", "/api/contests/" + id + "/shows/" + id, "{\"showNumber\":1,\"name\":\"Renamed Show\"}").statusCode()).isEqualTo(200);
        assertThat(service.standings(id).rows().stream().mapToInt(ContestStandingsResponse.Row::totalPoints).sum()).isEqualTo(140);
        assertThat(send("POST", closurePath(id) + "/reopen", "").statusCode()).isEqualTo(200);
        assertThat(service.standings(id).includedShowIds()).isEmpty();
        // Result reopening must not bypass the independent historical list lock.
        assertThat(send("POST", "/api/shows/" + id + "/entries/entry-list/reopen", "").body()).contains("PUBLISHED_BALLOTS_EXIST");
        assertThat(send("POST", "/api/shows/" + id + "/published-ballots/import", ballot(id, true)).statusCode()).isEqualTo(200);
        service.close(id);
        assertThat(service.standings(id).rows().getFirst().participationId()).isEqualTo(id + 15);
    }

    @Test
    void preservesCurrentToHistoricalClosureAndRoundtripsNativeAndJson() {
        long id = fixture();
        assertThat(send("POST", "/api/contests/" + id + "/make-current", "").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT entry_list_complete FROM motto_show WHERE id = ?", Integer.class, id)).isZero();
        // The personal-open entry projection masks assignments, but closure uses canonical IDs.
        service.close(id);
        var before = service.standings(id);
        assertThat(send("POST", "/api/contests/1/make-current", "").statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT entry_list_complete FROM motto_show WHERE id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(service.standings(id)).isEqualTo(before);
        byte[] json = exports.exportJson();
        var backup = backups.create(BackupReason.MANUAL);
        service.reopen(id);
        restores.restore(restores.previewUploadedJson(new ByteArrayInputStream(json), "s1.json").token());
        assertThat(service.standings(id)).isEqualTo(before);
        service.reopen(id);
        restores.restore(restores.previewKnownBackup(backup.id()).token());
        assertThat(service.standings(id)).isEqualTo(before);
        service.reopen(id);
        // Correct through the allowed current-show assignment path after changing the edition back.
        send("POST", "/api/contests/" + id + "/make-current", "");
        preparePersonalBallot(id, id + 19);
        assertThat(send("PUT", "/api/shows/" + id + "/entries/" + (id + 18) + "/participant", "{\"participantId\":null}").statusCode()).isEqualTo(200);
        assertThat(send("POST", closurePath(id) + "/close", "").statusCode()).isEqualTo(409);
        assertThat(closure(id).reasons()).anyMatch(r -> r.contains("Zuordnungen"));
        // Open/reopened state also survives export; no stale proof recreates closure.
        restores.restore(restores.previewUploadedJson(new ByteArrayInputStream(exports.exportJson()), "reopened.json").token());
        assertThat(closure(id).closedAt()).isNull();
    }

    @Test
    void protectsCurrentDirectAndIndirectAssignmentsButNotPersonalSnapshotsOrMetadata() {
        long id = fixture();
        send("POST", "/api/contests/" + id + "/make-current", "");
        preparePersonalBallot(id, id + 19);
        service.close(id);
        var before = service.standings(id);
        assertThat(send("PUT", "/api/shows/" + id + "/entries/" + (id + 18) + "/participant", "{\"participantId\":null}").body()).contains("SHOW_RESULT_CLOSED");
        assertThat(send("POST", "/api/shows/" + id + "/entries/assignment-import", "{\"assignments\":[{\"entryId\":" + (id + 18) + ",\"participationId\":" + (id + 19) + ",\"expectedParticipationId\":" + (id + 18) + ",\"confirmReplacement\":true}]}").statusCode()).isEqualTo(409);
        assertThat(send("POST", "/api/shows/" + id + "/entries/import", "{\"entries\":[]}").body()).contains("SHOW_RESULT_CLOSED");
        assertThat(send("PATCH", "/api/shows/" + id + "/entries/" + (id + 18), "{\"artist\":\"Cosmetic\",\"title\":\"Song\",\"youtubeUrl\":\"https://www.youtube.com/watch?v=dQw4w9WgXcQ\"}").statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/shows/" + id + "/ballot/reopen", "").statusCode()).isEqualTo(200);
        assertThat(service.standings(id).rows().stream().map(ContestStandingsResponse.Row::totalPoints)).containsExactlyElementsOf(before.rows().stream().map(ContestStandingsResponse.Row::totalPoints).toList());
        assertThat(send("PUT", "/api/shows/" + id + "/entries/own-entry-resolution", "{\"resolution\":\"NO_OWN_ENTRY\"}").body()).contains("SHOW_RESULT_CLOSED");
        service.reopen(id);
        // Establish an actual own-entry assignment through the supported path.
        send("PUT", "/api/contests/" + id + "/own-participation", "{\"participationId\":" + (id + 18) + ",\"confirmChange\":true}");
        assertThat(send("PUT", "/api/shows/" + id + "/entries/own-entry-resolution", "{\"resolution\":\"OWN_ENTRY\",\"entryId\":" + (id + 18) + "}").statusCode()).isEqualTo(204);
        service.close(id);
        assertThat(send("PUT", "/api/contests/" + id + "/own-participation", "{\"participationId\":" + (id + 19) + ",\"confirmChange\":true}").body()).contains("SHOW_RESULT_CLOSED");
        assertThat(jdbc.queryForObject("SELECT own_participation_id FROM contest WHERE id = ?", Long.class, id)).isEqualTo(id + 18);
    }

    @Test
    void serializesCloseAgainstParticipationCorrectionAndReopening() throws Exception {
        long id = fixture();
        CountDownLatch start = new CountDownLatch(1);
        var close = CompletableFuture.supplyAsync(() -> { await(start); return send("POST", closurePath(id) + "/close", ""); });
        var add = CompletableFuture.supplyAsync(() -> { await(start); return send("POST", "/api/contests/" + id + "/participants", "{\"displayName\":\"Concurrent\",\"countryCode\":\"DE\"}"); });
        start.countDown();
        int closed = close.get(20, TimeUnit.SECONDS).statusCode();
        int added = add.get(20, TimeUnit.SECONDS).statusCode();
        assertThat(List.of(closed, added)).isIn(List.of(200, 409), List.of(409, 201));
        if (closed == 200) {
            var reopen = CompletableFuture.supplyAsync(() -> send("POST", closurePath(id) + "/reopen", ""));
            var correct = CompletableFuture.supplyAsync(() -> send("PUT", ballotPath(id, id + 19), "{\"status\":\"UNERFASST\"}"));
            assertThat(reopen.get(20, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
            assertThat(correct.get(20, TimeUnit.SECONDS).statusCode()).isIn(204, 409);
        }
        assertThat(service.standings(id).includedShowIds()).isEmpty();
    }

    @Test
    void rejectsCorruptClosuresBeforeEitherRestorePathCanTouchLiveData() {
        long id = fixture();
        jdbc.update("UPDATE motto_show SET result_closed_at = '2026-10-08T00:00:00Z' WHERE id = ?", id);
        jdbc.update("DELETE FROM published_ballot_position WHERE published_ballot_id = ? AND rank = 15", id + 19);
        byte[] corrupt = exports.exportJson();
        var artifact = backups.create(BackupReason.MANUAL);
        jdbc.update("INSERT INTO published_ballot_position(published_ballot_id,contest_entry_id,rank) VALUES (?,?,15)", id + 19, id + 15);
        var live = service.standings(id);
        assertThatThrownBy(() -> restores.previewUploadedJson(new ByteArrayInputStream(corrupt), "broken.json")).isInstanceOf(BackupFileException.class);
        assertThatThrownBy(() -> restores.previewKnownBackup(artifact.id())).isInstanceOf(BackupFileException.class).hasMessageContaining("Stimmzettel");
        assertThat(service.standings(id)).isEqualTo(live);
    }

    @Test
    void importsMarkerlessVersionTenAsOpenAndRequiresTheNewFieldInVersionEleven() throws Exception {
        long id = fixture();
        service.close(id);
        var current = mapper.readTree(exports.exportJson());
        var old = (tools.jackson.databind.node.ObjectNode) current.deepCopy();
        old.put("formatVersion", 10);
        for (var show : old.path("data").path("mottoShows")) ((tools.jackson.databind.node.ObjectNode) show).remove("resultClosedAt");
        Path file = Files.createTempFile(ROOT, "v10-", ".json");
        Files.write(file, mapper.writeValueAsBytes(old));
        assertThat(exports.readAndValidate(file).data().mottoShows()).allSatisfy(s -> assertThat(s.resultClosedAt()).isNull());
        old.put("formatVersion", 11);
        Files.write(file, mapper.writeValueAsBytes(old));
        assertThatThrownBy(() -> exports.readAndValidate(file)).isInstanceOf(BackupFileException.class);
    }

    @Test
    void returnsOneConsistentContestSnapshotWhileClosuresAndNativeRestoresOverlap() throws Exception {
        long id = fixture();
        service.close(id);
        var closedBackup = backups.create(BackupReason.MANUAL);
        service.reopen(id);
        var openBackup = backups.create(BackupReason.MANUAL);
        String closedToken = restores.previewKnownBackup(closedBackup.id()).token();
        String openToken = restores.previewKnownBackup(openBackup.id()).token();
        CountDownLatch start = new CountDownLatch(1);
        var reading = CompletableFuture.runAsync(() -> {
            await(start);
            for (int i = 0; i < 30; i++) {
                var response = service.standings(id);
                boolean closed = !response.includedShowIds().isEmpty();
                assertThat(response.shows().getFirst().status().equals("CLOSED")).isEqualTo(closed);
                assertThat(response.rows()).allSatisfy(row -> {
                    assertThat(row.rank() != null).isEqualTo(closed);
                    assertThat(row.history().getFirst().included()).isEqualTo(closed);
                    assertThat(!row.shows().getFirst().state().equals("NOT_COUNTED")).isEqualTo(closed);
                });
                assertThat(response.rows().stream().mapToInt(ContestStandingsResponse.Row::totalPoints).sum()).isEqualTo(closed ? 140 : 0);
            }
        });
        var changing = CompletableFuture.runAsync(() -> {
            await(start);
            restores.restore(closedToken);
            service.reopen(id);
            service.close(id);
            restores.restore(openToken);
        });
        start.countDown();
        CompletableFuture.allOf(reading, changing).get(30, TimeUnit.SECONDS);
        assertThat(service.standings(id).includedShowIds()).isEmpty();
    }

    @Test
    void forwardsStandingsAndExistingEvaluationUrlsToTheBundledSpa() {
        for (String path : List.of("/standings", "/shows/1/evaluation", "/shows/1/result", "/shows/1/published-ballots")) {
            assertThat(send("GET", path, null).body()).contains("<html");
        }
    }

    private long fixture() {
        long id = IDS.getAndAdd(100);
        jdbc.update("INSERT INTO contest(id,name,display_order,is_current,created_at,updated_at) VALUES (?,?,?,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, "Synthetic " + id, id);
        jdbc.update("INSERT INTO motto_show(id,contest_id,show_number,name,entry_list_complete,created_at,updated_at) VALUES (?,?,1,'Synthetic Show',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id, id);
        for (int i = 1; i <= 19; i++) {
            jdbc.update("INSERT INTO participant(id,display_name,active,created_at,updated_at) VALUES (?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id + i, "Synthetic " + (id + i));
            jdbc.update("INSERT INTO contest_participation(id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (?,?,?,'DE',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id + i, id, id + i, i == 18 ? 0 : 1);
            if (i < 19) jdbc.update("INSERT INTO contest_entry(id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at) VALUES (?,?,?,'Synthetic Artist',?,'https://www.youtube.com/watch?v=dQw4w9WgXcQ',?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id + i, id, id, "Song " + i, i, id + i);
            jdbc.update("INSERT INTO published_ballot(id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) VALUES (?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)", id + i, id, id, id + i, i == 19 ? "ABGESTIMMT" : "NICHT_ABGESTIMMT");
        }
        for (int i = 1; i <= 15; i++) jdbc.update("INSERT INTO published_ballot_position(published_ballot_id,contest_entry_id,rank) VALUES (?,?,?)", id + 19, id + i, i);
        return id;
    }
    private void preparePersonalBallot(long id, long ownId) {
        assertThat(send("PUT", "/api/contests/" + id + "/own-participation", "{\"participationId\":" + ownId + ",\"confirmChange\":true}").statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/shows/" + id + "/entries/own-entry-resolution", "{\"resolution\":\"NO_OWN_ENTRY\"}").statusCode()).isEqualTo(204);
        assertThat(send("PUT", "/api/shows/" + id + "/ballot/reorder", mapper.writeValueAsString(Map.of("rankedEntryIds", IntStream.rangeClosed(1,15).mapToObj(i -> id + i).toList(), "unrankedEntryIds", List.of(id + 16, id + 17, id + 18)))).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/shows/" + id + "/ballot/close", "").statusCode()).isEqualTo(200);
    }
    private String ballot(long id, boolean reversed) {
        return mapper.writeValueAsString(Map.of("ballots", List.of(Map.of("participationId", id + 19, "replaceExisting", true,
                "positions", IntStream.rangeClosed(1,15).mapToObj(i -> Map.of("entryId", id + i, "rank", reversed ? 16 - i : i)).toList()))));
    }
    private ShowResultClosureResponse closure(long id) { return service.closure(id); }
    private static String closurePath(long id) { return "/api/shows/" + id + "/result-closure"; }
    private static String ballotPath(long show, long participant) { return "/api/shows/" + show + "/published-ballots/" + participant + "/status"; }
    private HttpResponse<String> send(String method, String path, String body) {
        try {
            var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
            if (body == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception failure) { throw new IllegalStateException(failure); }
    }
    private static void await(CountDownLatch latch) { try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Start timed out"); } catch (InterruptedException e) { throw new IllegalStateException(e); } }
    private static Path storage() { try { return Files.createTempDirectory("s1-results-"); } catch (Exception e) { throw new IllegalStateException(e); } }
}
