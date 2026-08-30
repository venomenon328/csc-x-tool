package de.venomenon.cscxtool.tips;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.data.ExportFormat;
import de.venomenon.cscxtool.data.ExportService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class TipsGameReviewRegressionTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private TipsGameService service;
    @Autowired private ExportService exports;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void draftExposesOnlyRevealCompletenessAndNeverActualAssignments() {
        Fixture fixture = fixture();
        service.replace(fixture.showId(), new SaveTipsGameRequest(List.of(
                new SaveTipsAssignmentRequest(fixture.firstEntryId(), fixture.firstParticipationId(), "HIGH", "Tipp")
        )));
        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", fixture.firstParticipationId(), fixture.firstEntryId());
        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE id = ?", fixture.secondParticipationId(), fixture.secondEntryId());

        TipsGameResponse draft = service.detail(fixture.showId());
        assertThat(draft.status()).isEqualTo(TipsGameStatus.DRAFT);
        assertThat(draft.actualAssignmentsComplete()).isTrue();
        assertThat(draft.entries()).allSatisfy(entry -> assertThat(entry.actualAssignment()).isNull());

        TipsGameResponse resolved = service.resolve(fixture.showId());
        assertThat(resolved.status()).isEqualTo(TipsGameStatus.RESOLVED);
        assertThat(resolved.entries()).extracting(TipsEntryResponse::actualAssignment).doesNotContainNull();

        TipsGameResponse reopened = service.reopen(fixture.showId());
        assertThat(reopened.status()).isEqualTo(TipsGameStatus.DRAFT);
        assertThat(reopened.actualAssignmentsComplete()).isTrue();
        assertThat(reopened.entries()).allSatisfy(entry -> assertThat(entry.actualAssignment()).isNull());
    }

    @Test
    void currentContestHistoryIncludesEarlierShowsOnlyAfterTheirRevealIsComplete() {
        Fixture fixture = fixture();
        int sequence = SEQUENCE.get();
        int earlierNumber = 50_000 + sequence;
        jdbc.update("""
                INSERT INTO motto_show (contest_id,show_number,name,created_at,updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, fixture.contestId(), earlierNumber, "Frühere Tipp-Show " + sequence);
        long earlierShowId = jdbc.queryForObject(
                "SELECT id FROM motto_show WHERE contest_id = ? AND show_number = ?", Long.class, fixture.contestId(), earlierNumber
        );
        jdbc.update("""
                INSERT INTO contest_entry (motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                VALUES (?, ?, 'Früherer Artist', 'Früherer Song', 'https://example.test/prior', 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, earlierShowId, fixture.contestId(), fixture.firstParticipationId());
        jdbc.update("""
                INSERT INTO contest_entry (motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                VALUES (?, ?, 'Noch anonym', 'Noch unbekannt', 'https://example.test/unknown', 2, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, earlierShowId, fixture.contestId());

        TipsSubmissionHistoryResponse incomplete = service.history(fixture.showId(), fixture.firstParticipationId());
        assertThat(incomplete.entries()).extracting(TipsSubmissionHistoryResponseItem::title).doesNotContain("Früherer Song");

        jdbc.update("UPDATE contest_entry SET contest_participation_id = ? WHERE motto_show_id = ? AND pool_position = 2",
                fixture.secondParticipationId(), earlierShowId);
        TipsSubmissionHistoryResponse complete = service.history(fixture.showId(), fixture.firstParticipationId());
        assertThat(complete.entries()).anySatisfy(entry -> {
            assertThat(entry.title()).isEqualTo("Früherer Song");
            assertThat(entry.youtubeUrl()).isEqualTo("https://example.test/prior");
        });
    }

    @Test
    void v8ExportRemainsValidWhenAContestWithTipsBecomesHistorical() throws Exception {
        Fixture fixture = fixture();
        service.replace(fixture.showId(), new SaveTipsGameRequest(List.of(
                new SaveTipsAssignmentRequest(fixture.firstEntryId(), fixture.firstParticipationId(), "MEDIUM", "bleibt erhalten")
        )));
        long nextContestId = createContest("Folgecontest " + SEQUENCE.get());
        Path exportFile = Files.createTempFile(STORAGE_ROOT, "tips-former-current-", ".json");
        try {
            jdbc.update("UPDATE contest SET is_current = CASE WHEN id = ? THEN 1 ELSE 0 END", nextContestId);
            Files.write(exportFile, exports.exportJson());
            ExportFormat.FullExport validated = exports.readAndValidate(exportFile);
            assertThat(validated.data().tipsGames()).anySatisfy(game -> assertThat(game.mottoShowId()).isEqualTo(fixture.showId()));
        } finally {
            jdbc.update("UPDATE contest SET is_current = CASE WHEN id = ? THEN 1 ELSE 0 END", fixture.contestId());
            Files.deleteIfExists(exportFile);
        }
    }

    private Fixture fixture() {
        int sequence = SEQUENCE.incrementAndGet();
        long contestId = jdbc.queryForObject("SELECT id FROM contest WHERE is_current = 1", Long.class);
        int showNumber = 90_000 + sequence;
        jdbc.update("""
                INSERT INTO motto_show (contest_id,show_number,name,created_at,updated_at)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, contestId, showNumber, "Tipp-Regression " + sequence);
        long showId = jdbc.queryForObject(
                "SELECT id FROM motto_show WHERE contest_id = ? AND show_number = ?", Long.class, contestId, showNumber
        );
        long firstParticipantId = participant("Tipp Regression A " + sequence);
        long secondParticipantId = participant("Tipp Regression B " + sequence);
        long firstParticipationId = participation(contestId, firstParticipantId, "DE");
        long secondParticipationId = participation(contestId, secondParticipantId, "FR");
        long firstEntryId = entry(showId, contestId, "Alpha", "Eins", 1);
        long secondEntryId = entry(showId, contestId, "Beta", "Zwei", 2);
        return new Fixture(contestId, showId, firstParticipationId, secondParticipationId, firstEntryId, secondEntryId);
    }

    private long participant(String displayName) {
        jdbc.update("""
                INSERT INTO participant (display_name,active,created_at,updated_at)
                VALUES (?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, displayName);
        return jdbc.queryForObject("SELECT id FROM participant WHERE display_name = ?", Long.class, displayName);
    }

    private long participation(long contestId, long participantId, String countryCode) {
        jdbc.update("""
                INSERT INTO contest_participation (contest_id,participant_id,country_code,active,created_at,updated_at)
                VALUES (?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, contestId, participantId, countryCode);
        return jdbc.queryForObject(
                "SELECT id FROM contest_participation WHERE contest_id = ? AND participant_id = ?", Long.class, contestId, participantId
        );
    }

    private long entry(long showId, long contestId, String artist, String title, int position) {
        jdbc.update("""
                INSERT INTO contest_entry (motto_show_id,contest_id,artist,title,youtube_url,pool_position,created_at,updated_at)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, showId, contestId, artist, title, "https://example.test/" + position, position);
        return jdbc.queryForObject(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND pool_position = ?", Long.class, showId, position
        );
    }

    private long createContest(String name) {
        int displayOrder = jdbc.queryForObject("SELECT COALESCE(MAX(display_order),0) + 1 FROM contest", Integer.class);
        jdbc.update("""
                INSERT INTO contest (name,display_order,is_current,created_at,updated_at)
                VALUES (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, name, displayOrder);
        return jdbc.queryForObject("SELECT id FROM contest WHERE name = ?", Long.class, name);
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-tips-review-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht erstellt werden.", exception);
        }
    }

    private record Fixture(
            long contestId, long showId, long firstParticipationId, long secondParticipationId,
            long firstEntryId, long secondEntryId
    ) { }
}
