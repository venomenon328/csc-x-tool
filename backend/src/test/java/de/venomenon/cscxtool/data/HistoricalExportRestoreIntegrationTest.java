package de.venomenon.cscxtool.data;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.system.ApplicationStorage;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class HistoricalExportRestoreIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private ApplicationStorage storage;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private ExportService exports;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws Exception {
        Path root = temporaryDirectory.resolve("storage");
        storage = new ApplicationStorage(
                root,
                root.resolve("data"),
                root.resolve("data/csc-x-tool.db"),
                root.resolve("backups/automatic"),
                root.resolve("backups/manual"),
                root.resolve("exports"),
                root.resolve("logs"),
                root.resolve("runtime")
        );
        Files.createDirectories(storage.dataDirectory());
        Files.createDirectories(storage.automaticBackupsDirectory());
        Files.createDirectories(storage.manualBackupsDirectory());
        Files.createDirectories(storage.exportsDirectory());
        Files.createDirectories(storage.logsDirectory());
        Files.createDirectories(storage.runtimeDirectory());

        dataSource = SqliteDataSourceFactory.create(storage.databaseFile());
        SchemaSupport.migrate(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        objectMapper = new ObjectMapper();
        BackupService backups = new BackupService(
                storage, dataSource, new SqliteOnlineBackupAdapter(), objectMapper
        );
        exports = new ExportService(dataSource, objectMapper, backups);
    }

    @Test
    void roundTripsACompletedHistoricalSongListWithAMissingSourceUrl() throws Exception {
        insertCompletedHistoricalSongListWithoutUrl();

        Path jsonFile = temporaryDirectory.resolve("historical-v7.json");
        Files.write(jsonFile, exports.exportJson());
        ExportFormat.FullExport validated = exports.readAndValidate(jsonFile);
        Path restoredDatabase = temporaryDirectory.resolve("historical-restored.db");

        exports.restoreInto(restoredDatabase, validated);

        JdbcTemplate restored = new JdbcTemplate(SqliteDataSourceFactory.create(restoredDatabase));
        assertThat(restored.queryForObject(
                "SELECT entry_list_complete FROM motto_show WHERE id = 300", Boolean.class
        )).isTrue();
        assertThat(restored.queryForObject(
                "SELECT youtube_url FROM contest_entry WHERE id = 400", String.class
        )).isNull();
        assertThat(restored.queryForObject("""
                SELECT participant.display_name
                FROM contest_entry entry
                JOIN contest_participation participation ON participation.id = entry.contest_participation_id
                JOIN participant ON participant.id = participation.participant_id
                WHERE entry.id = 400
                """, String.class)).isEqualTo("Archivnutzer");
    }

    @Test
    void roundTripsAnIncompleteTipsDraftWithConfidenceAndMultilineNote() throws Exception {
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (901,'Tippidentität',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("""
                INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at)
                VALUES (901,1,901,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,comment,assessment,assessment_confidence,
                                           pool_position,ranking_position,contest_participation_id,created_at,updated_at)
                VALUES (901,1,1,'Anonym','Entwurf','https://www.youtube.com/watch?v=dQw4w9WgXcQ',NULL,NULL,NULL,1,NULL,NULL,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO tips_game (id,motto_show_id,contest_id,status,created_at,updated_at,resolved_at)
                VALUES (901,1,1,'DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,NULL)
                """);
        jdbc.update("""
                INSERT INTO tips_game_assignment (id,tips_game_id,contest_entry_id,guessed_participation_id,confidence,note)
                VALUES (901,901,901,901,'HIGH','erste Zeile' || char(10) || 'zweite Zeile')
                """);

        Path source = temporaryDirectory.resolve("tips-v8.json");
        Files.write(source, exports.exportJson());
        Path restoredDatabase = temporaryDirectory.resolve("tips-restored.db");
        exports.restoreInto(restoredDatabase, exports.readAndValidate(source));

        JdbcTemplate restored = new JdbcTemplate(SqliteDataSourceFactory.create(restoredDatabase));
        assertThat(restored.queryForObject("SELECT status FROM tips_game WHERE id = 901", String.class)).isEqualTo("DRAFT");
        assertThat(restored.queryForObject("SELECT confidence FROM tips_game_assignment WHERE id = 901", String.class)).isEqualTo("HIGH");
        assertThat(restored.queryForObject("SELECT note FROM tips_game_assignment WHERE id = 901", String.class)).isEqualTo("erste Zeile\nzweite Zeile");
    }

    @Test
    void upgradesTheP13JsonShapeToAnEmptyTipsSection() throws Exception {
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        ExportFormat.FullExportV7 legacy = new ExportFormat.FullExportV7(ExportFormat.FORMAT, ExportFormat.VERSION_7,
                current.exportedAt(), current.applicationVersion(), 13, new ExportFormat.DataV7(data.contests(), data.mottoShows(),
                data.candidates(), data.participants(), data.contestParticipations(), data.participantAliases(), data.contestEntries(),
                data.ballotSnapshots(), data.ballotSnapshotItems(), data.legacyResults(), data.legacyReceivedScores(),
                data.publishedBallots(), data.publishedBallotPositions()));
        Path source = temporaryDirectory.resolve("p13-v7.json");
        Files.write(source, objectMapper.writeValueAsBytes(legacy));

        ExportFormat.FullExport upgraded = exports.readAndValidate(source);

        assertThat(upgraded.formatVersion()).isEqualTo(ExportFormat.VERSION);
        assertThat(upgraded.data().tipsGames()).isEmpty();
        assertThat(upgraded.data().tipsGameAssignments()).isEmpty();
    }

    @Test
    void upgradesFormatV4WithHistoricalCompletionDefaultingToOpen() throws Exception {
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        ExportFormat.FullExportV4 legacy = new ExportFormat.FullExportV4(
                ExportFormat.FORMAT,
                ExportFormat.VERSION_4,
                current.exportedAt(),
                current.applicationVersion(),
                10,
                new ExportFormat.DataV4(
                        data.contests().stream().map(contest -> new ExportFormat.ContestV6(
                                contest.id(), contest.name(), contest.displayOrder(), contest.current(), contest.createdAt(), contest.updatedAt()
                        )).toList(),
                        data.mottoShows().stream().map(show -> new ExportFormat.MottoShowV4(
                                show.id(), show.contestId(), show.showNumber(), show.name(), show.selectedCandidateId(),
                                show.ballotClosedAt(), null, null, false, null, show.createdAt(), show.updatedAt()
                        )).toList(),
                        data.candidates(),
                        data.participants(),
                        data.contestParticipations(),
                        data.participantAliases(),
                        data.contestEntries(),
                        data.ballotSnapshots(),
                        data.ballotSnapshotItems(),
                        data.legacyReceivedScores().stream().map(score -> new ExportFormat.ReceivedScore(
                                score.id(), score.mottoShowId(), score.contestId(), score.contestParticipationId(), score.status(),
                                score.points(), score.createdAt(), score.updatedAt()
                        )).toList()
                )
        );
        Path jsonFile = temporaryDirectory.resolve("legacy-v4.json");
        Files.write(jsonFile, objectMapper.writeValueAsBytes(legacy));

        ExportFormat.FullExport upgraded = exports.readAndValidate(jsonFile);

        assertThat(upgraded.formatVersion()).isEqualTo(ExportFormat.VERSION);
        assertThat(upgraded.data().mottoShows()).allMatch(show -> !show.entryListComplete());

        Path restoredDatabase = temporaryDirectory.resolve("legacy-v4-restored.db");
        exports.restoreInto(restoredDatabase, upgraded);
        JdbcTemplate restored = new JdbcTemplate(SqliteDataSourceFactory.create(restoredDatabase));
        assertThat(restored.queryForObject(
                "SELECT COUNT(*) FROM motto_show WHERE entry_list_complete <> 0", Integer.class
        )).isZero();
    }

    private void insertCompletedHistoricalSongListWithoutUrl() {
        jdbc.update("""
                INSERT INTO contest (id, name, display_order, is_current, created_at, updated_at)
                VALUES (2, 'CSC IX', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO participant (id, display_name, active, created_at, updated_at)
                VALUES (100, 'Archivnutzer', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_participation (
                  id, contest_id, participant_id, country_code, active, created_at, updated_at
                ) VALUES (200, 2, 100, 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO motto_show (
                  id, contest_id, show_number, name, entry_list_complete, created_at, updated_at
                ) VALUES (300, 2, 3, 'Archivshow', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_entry (
                  id, motto_show_id, contest_id, artist, title, youtube_url, pool_position,
                  contest_participation_id, created_at, updated_at
                ) VALUES (400, 300, 2, 'Ohne Link', 'Historischer Song', NULL, 1, 200, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
    }
}
