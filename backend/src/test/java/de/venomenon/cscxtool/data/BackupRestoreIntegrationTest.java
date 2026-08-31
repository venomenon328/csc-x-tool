package de.venomenon.cscxtool.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.cscxtool.system.ApplicationStorage;
import de.venomenon.cscxtool.system.DatabaseStartupState;
import de.venomenon.cscxtool.system.LiquibaseStartupCoordinator;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

class BackupRestoreIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    private ApplicationStorage storage;
    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private BackupService backups;
    private ExportService exports;
    private RestoreService restores;
    private DatabaseAccessLock lock;

    @BeforeEach
    void setUp() throws Exception {
        Path root = temporaryDirectory.resolve("storage");
        storage = new ApplicationStorage(root, root.resolve("data"), root.resolve("data/csc-x-tool.db"),
                root.resolve("backups/automatic"), root.resolve("backups/manual"), root.resolve("exports"),
                root.resolve("logs"), root.resolve("runtime"));
        Files.createDirectories(storage.dataDirectory());
        Files.createDirectories(storage.automaticBackupsDirectory());
        Files.createDirectories(storage.manualBackupsDirectory());
        Files.createDirectories(storage.exportsDirectory());
        Files.createDirectories(storage.logsDirectory());
        Files.createDirectories(storage.runtimeDirectory());
        DataSource sqlite = SqliteDataSourceFactory.create(storage.databaseFile());
        lock = new DatabaseAccessLock();
        dataSource = new LockedDataSource(sqlite, lock);
        SchemaSupport.migrate(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        backups = new BackupService(storage, dataSource, new SqliteOnlineBackupAdapter(), new ObjectMapper());
        exports = new ExportService(dataSource, new ObjectMapper(), backups);
        restores = new RestoreService(storage, backups, exports, new SqliteOnlineBackupAdapter(), dataSource, lock);
    }

    @Test
    void createsReadableOnlineBackupFromALiveWalDatabaseAndRetainsAutomaticBackups() throws Exception {
        insertFullData("Wal; \"Kommentar\"\nzweite Zeile");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(statement.executeQuery("PRAGMA journal_mode").getString(1)).isEqualToIgnoringCase("wal");
        }
        BackupSummary manual = backups.create(BackupReason.MANUAL);
        Path extractionDirectory = temporaryDirectory.resolve("extracted");
        Files.createDirectories(extractionDirectory);
        Path extracted = backups.extractVerifiedSnapshot(backups.resolveKnownArtifact(manual.id()), extractionDirectory);
        assertThat(new JdbcTemplate(SqliteDataSourceFactory.create(extracted)).queryForObject("SELECT COUNT(*) FROM candidate", Integer.class)).isEqualTo(1);

        for (int index = 0; index < 31; index++) backups.create(BackupReason.STARTUP);
        assertThat(backups.overview().automaticBackups()).hasSize(30);
        assertThat(backups.overview().manualBackups()).extracting(BackupSummary::id).contains(manual.id());
    }

    @Test
    void restoresNativeBackupOnlyAfterPreviewAndKeepsTheOlderState() {
        insertFullData("Vorher");
        BackupSummary old = backups.create(BackupReason.MANUAL);
        jdbc.update("UPDATE candidate SET comment = 'Nachher'");

        RestorePreview preview = restores.previewKnownBackup(old.id());
        assertThat(preview.compatible()).isTrue();
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Nachher");
        RestoreResult restored = restores.restore(preview.token());

        assertThat(restored.safetyBackup().reason()).isEqualTo(BackupReason.PRE_RESTORE);
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Vorher");
        assertThat(backups.overview().manualBackups()).anyMatch(backup -> backup.reason() == BackupReason.PRE_RESTORE);
    }

    @Test
    void restoresAnOlderCompatibleP5BackupThroughTheNativePreviewPath() throws Exception {
        Path p5Database = temporaryDirectory.resolve("p5-restore-source.db");
        DataSource p5Source = SqliteDataSourceFactory.create(p5Database);
        migrate(p5Source, "classpath:/db/changelog/p5-master.yaml");
        JdbcTemplate p5 = new JdbcTemplate(p5Source);
        p5.update("""
                INSERT INTO candidate (motto_show_id,artist,title,youtube_url,status,manual_position,created_at,updated_at)
                VALUES (1,'P5','Historischer Stand','https://www.youtube.com/watch?v=dQw4w9WgXcQ','OFFEN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        BackupSummary p5Backup = new BackupService(storage, p5Source, new SqliteOnlineBackupAdapter(), new ObjectMapper())
                .create(BackupReason.MANUAL);
        jdbc.update("""
                INSERT INTO candidate (motto_show_id,artist,title,youtube_url,status,manual_position,created_at,updated_at)
                VALUES (1,'Live','Neuer Stand','https://www.youtube.com/watch?v=dQw4w9WgXcQ','OFFEN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);

        RestorePreview preview = restores.previewKnownBackup(p5Backup.id());
        assertThat(preview.schemaVersion()).isEqualTo(6);
        restores.restore(preview.token());

        assertThat(jdbc.queryForObject("SELECT title FROM candidate WHERE motto_show_id = 1", String.class)).isEqualTo("Historischer Stand");
        assertThat(SchemaSupport.schemaVersion(dataSource)).isEqualTo(SchemaSupport.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void roundTripsAllBusinessTablesViaJsonAndLeavesSequencesUsable() throws Exception {
        insertFullData("Ä; \"Zitat\"\nZeile");
        jdbc.update("UPDATE contest_entry SET pool_position = pool_position + 100 WHERE motto_show_id = 1");
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("UPDATE contest_entry SET pool_position = ? WHERE id = ?", 16 - rank, 199 + rank);
        }
        byte[] json = exports.exportJson();
        assertThat(new String(json, StandardCharsets.UTF_8)).contains("\"formatVersion\":9", "\"assessment\":3", "\"assessmentConfidence\":1", "\"legacyReceivedScores\"")
                .doesNotContain("\"listened\"", "\"relisten\"");
        jdbc.update("DELETE FROM legacy_received_score");
        jdbc.update("DELETE FROM ballot_snapshot_item");
        jdbc.update("DELETE FROM ballot_snapshot");
        jdbc.update("DELETE FROM contest_entry");
        jdbc.update("UPDATE motto_show SET selected_candidate_id = NULL");
        jdbc.update("DELETE FROM candidate");
        jdbc.update("DELETE FROM contest_participation");
        jdbc.update("DELETE FROM participant_alias");
        jdbc.update("DELETE FROM participant");

        RestorePreview preview = restores.previewUploadedJson(new ByteArrayInputStream(json), "voll.json");
        assertThat(preview.counts()).extracting(RestoreDataCounts::candidates, RestoreDataCounts::participants,
                RestoreDataCounts::contestEntries, RestoreDataCounts::ballotSnapshots, RestoreDataCounts::legacyReceivedScores)
                .containsExactly(1, 1, 15, 1, 1);
        restores.restore(preview.token());

        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Ä; \"Zitat\"\nZeile");
        assertThat(jdbc.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE id = 400", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT points FROM legacy_received_score WHERE id = 500", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM contest_entry WHERE motto_show_id = 1 ORDER BY pool_position", Long.class))
                .containsExactly(214L, 213L, 212L, 211L, 210L, 209L, 208L, 207L, 206L, 205L, 204L, 203L, 202L, 201L, 200L);
        jdbc.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (1, 'Neu', 'Neu', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM candidate", Long.class)).isGreaterThan(100L);
    }

    @Test
    void importsLegacyV1JsonWithDeterministicPoolPositionsAndAssessmentMapping() throws Exception {
        insertFullData("Legacy");
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        List<ExportFormat.ContestEntryV1> legacyEntries = data.contestEntries().stream().map(entry -> new ExportFormat.ContestEntryV1(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                entry.id() != 200 && entry.id() != 201, entry.id() == 201 || entry.id() == 202,
                entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
        ExportFormat.FullExportV1 legacy = new ExportFormat.FullExportV1(
                ExportFormat.FORMAT, ExportFormat.LEGACY_VERSION, current.exportedAt(), current.applicationVersion(), current.schemaVersion(),
                legacyDataV1(data, legacyEntries)
        );
        byte[] json = new ObjectMapper().writeValueAsBytes(legacy);
        jdbc.update("DELETE FROM legacy_received_score");
        jdbc.update("DELETE FROM ballot_snapshot_item");
        jdbc.update("DELETE FROM ballot_snapshot");
        jdbc.update("DELETE FROM contest_entry");

        RestorePreview preview = restores.previewUploadedJson(new ByteArrayInputStream(json), "legacy-v1.json");
        restores.restore(preview.token());

        assertThat(jdbc.queryForList("SELECT pool_position FROM contest_entry WHERE motto_show_id = 1 ORDER BY pool_position", Integer.class))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        assertThat(jdbc.queryForList("SELECT ranking_position FROM contest_entry WHERE motto_show_id = 1 ORDER BY ranking_position", Integer.class))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
        assertThat(jdbc.query("SELECT assessment, assessment_confidence FROM contest_entry WHERE id IN (200, 201, 202, 203) ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getObject(1) + ":" + resultSet.getObject(2)))
                .containsExactly("null:null", "null:null", "3:1", "3:2");
    }

    @Test
    void importsLegacyV2JsonWithAssessmentMapping() throws Exception {
        insertFullData("Legacy v2");
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        List<ExportFormat.ContestEntryV2> legacyEntries = data.contestEntries().stream().map(entry -> new ExportFormat.ContestEntryV2(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                entry.id() != 200 && entry.id() != 201, entry.id() == 201 || entry.id() == 202,
                entry.poolPosition(), entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
        ExportFormat.FullExportV2 legacy = new ExportFormat.FullExportV2(
                ExportFormat.FORMAT, ExportFormat.VERSION_2, current.exportedAt(), current.applicationVersion(), current.schemaVersion(),
                legacyDataV2(data, legacyEntries)
        );
        byte[] json = new ObjectMapper().writeValueAsBytes(legacy);
        jdbc.update("DELETE FROM legacy_received_score");
        jdbc.update("DELETE FROM ballot_snapshot_item");
        jdbc.update("DELETE FROM ballot_snapshot");
        jdbc.update("DELETE FROM contest_entry");

        restores.restore(restores.previewUploadedJson(new ByteArrayInputStream(json), "legacy-v2.json").token());

        assertThat(jdbc.query("SELECT assessment, assessment_confidence FROM contest_entry WHERE id IN (200, 201, 202, 203) ORDER BY id",
                (resultSet, rowNumber) -> resultSet.getObject(1) + ":" + resultSet.getObject(2)))
                .containsExactly("null:null", "null:null", "3:1", "3:2");
    }

    @Test
    void importsLegacyV3JsonWithTheSameCompleteStateValidation() throws Exception {
        insertFullData("Legacy v3");
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        ExportFormat.FullExportV3 legacy = new ExportFormat.FullExportV3(
                ExportFormat.FORMAT, ExportFormat.VERSION_3, current.exportedAt(), current.applicationVersion(), current.schemaVersion(),
                new ExportFormat.DataV3(
                        legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), legacyEntriesV3(data),
                        data.ballotSnapshots(), data.ballotSnapshotItems(), legacyScores(data)
                )
        );

        restores.restore(restores.previewUploadedJson(
                new ByteArrayInputStream(new ObjectMapper().writeValueAsBytes(legacy)), "legacy-v3.json"
        ).token());

        assertThat(jdbc.queryForObject("SELECT country_code FROM contest_participation WHERE id = 10", String.class)).isEqualTo("DE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ballot_snapshot_item WHERE ballot_snapshot_id = 300", Integer.class)).isEqualTo(15);
    }

    @Test
    void upgradesP5AndP6JsonScoresOnlyIntoTheSeparateLegacyArchive() throws Exception {
        insertFullData("Legacy P5/P6");
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        List<ExportFormat.ContestV6> contests = legacyContestsV6(data);
        List<ExportFormat.MottoShowV6> shows = legacyShowsV6(data);
        List<ExportFormat.ReceivedScore> scores = legacyScoresV6(data);
        List<Object> oldExports = List.of(
                new ExportFormat.FullExportV5(ExportFormat.FORMAT, ExportFormat.VERSION_5, current.exportedAt(), current.applicationVersion(), 11,
                        new ExportFormat.DataV5(contests, shows, data.candidates(), data.participants(), data.contestParticipations(),
                                data.participantAliases(), data.contestEntries(), data.ballotSnapshots(), data.ballotSnapshotItems(), scores)),
                new ExportFormat.FullExportV6(ExportFormat.FORMAT, ExportFormat.VERSION_6, current.exportedAt(), current.applicationVersion(), 12,
                        new ExportFormat.DataV6(contests, shows, data.candidates(), data.participants(), data.contestParticipations(),
                                data.participantAliases(), data.contestEntries(), data.ballotSnapshots(), data.ballotSnapshotItems(), scores,
                                data.publishedBallots(), data.publishedBallotPositions()))
        );

        for (int index = 0; index < oldExports.size(); index++) {
            Path source = temporaryDirectory.resolve("legacy-v" + (index + 5) + ".json");
            Files.write(source, new ObjectMapper().writeValueAsBytes(oldExports.get(index)));
            ExportFormat.FullExport upgraded = exports.readAndValidate(source);
            Path restoredDatabase = temporaryDirectory.resolve("legacy-v" + (index + 5) + ".db");
            exports.restoreInto(restoredDatabase, upgraded);
            JdbcTemplate restored = new JdbcTemplate(SqliteDataSourceFactory.create(restoredDatabase));

            assertThat(upgraded.formatVersion()).isEqualTo(ExportFormat.VERSION);
            assertThat(upgraded.data().legacyReceivedScores()).extracting(ExportFormat.LegacyReceivedScore::points).contains(0);
            assertThat(restored.queryForObject("SELECT points FROM legacy_received_score WHERE id=500", Integer.class)).isZero();
            assertThat(restored.queryForObject("SELECT COUNT(*) FROM published_ballot", Integer.class)).isZero();
        }
    }

    @Test
    void rejectsUnknownNewerJsonWithoutChangingLiveDatabase() throws Exception {
        insertFullData("Live");
        String newer = new String(exports.exportJson(), StandardCharsets.UTF_8).replace("\"formatVersion\":9", "\"formatVersion\":10");
        assertThatThrownBy(() -> restores.previewUploadedJson(new ByteArrayInputStream(newer.getBytes(StandardCharsets.UTF_8)), "new.json"))
                .isInstanceOf(BackupFileException.class).hasMessageContaining("nicht unterst");
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Live");
    }

    @Test
    void rejectsInvalidJsonMetadataAndBusinessInvariantsBeforeChangingTheLiveDatabase() throws Exception {
        insertFullData("Live");
        ExportFormat.FullExport valid = exports.snapshot();
        ExportFormat.Data data = valid.data();
        ExportFormat.ContestParticipation participation = data.contestParticipations().getFirst();
        ExportFormat.ContestParticipation invalidParticipation = new ExportFormat.ContestParticipation(
                participation.id(), participation.contestId(), participation.participantId(), "XX", participation.active(),
                participation.createdAt(), participation.updatedAt()
        );
        ExportFormat.Data invalidCountryData = dataWith(data, data.contests(), data.contestEntries(),
                List.of(invalidParticipation), data.ballotSnapshots(), data.ballotSnapshotItems(), data.legacyReceivedScores());
        ExportFormat.ContestEntry assessed = data.contestEntries().getFirst();
        ExportFormat.ContestEntry invalidAssessment = new ExportFormat.ContestEntry(
                assessed.id(), assessed.mottoShowId(), assessed.contestId(), assessed.artist(), assessed.title(), assessed.youtubeUrl(), assessed.comment(),
                4, null, assessed.poolPosition(), assessed.rankingPosition(), assessed.contestParticipationId(), assessed.createdAt(), assessed.updatedAt()
        );
        ExportFormat.Data invalidAssessmentData = dataWith(data, data.contests(),
                java.util.stream.Stream.concat(java.util.stream.Stream.of(invalidAssessment), data.contestEntries().stream().skip(1)).toList(),
                data.contestParticipations(), data.ballotSnapshots(), data.ballotSnapshotItems(), data.legacyReceivedScores());
        List<ExportFormat.FullExport> invalidExports = List.of(
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), "not-an-instant", valid.applicationVersion(), valid.schemaVersion(), data),
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), valid.exportedAt(), valid.applicationVersion(), valid.schemaVersion(),
                        dataWith(data, List.of(), data.contestEntries(), data.contestParticipations(), data.ballotSnapshots(), data.ballotSnapshotItems(), data.legacyReceivedScores())),
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), valid.exportedAt(), valid.applicationVersion(), valid.schemaVersion(), invalidCountryData),
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), valid.exportedAt(), valid.applicationVersion(), valid.schemaVersion(), invalidAssessmentData)
        );
        ObjectMapper mapper = new ObjectMapper();
        for (ExportFormat.FullExport invalid : invalidExports) {
            assertThatThrownBy(() -> restores.previewUploadedJson(
                    new ByteArrayInputStream(mapper.writeValueAsBytes(invalid)), "ungueltig.json"))
                    .isInstanceOf(BackupFileException.class);
        }
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Live");
    }

    @Test
    void rejectsMalformedV4LifecycleAndOrderingStatesBeforeStaging() throws Exception {
        insertFullData("Live");
        ExportFormat.FullExport valid = exports.snapshot();
        ExportFormat.Data data = valid.data();
        ExportFormat.ContestEntry firstEntry = data.contestEntries().getFirst();
        ExportFormat.ContestEntry poolGap = new ExportFormat.ContestEntry(
                firstEntry.id(), firstEntry.mottoShowId(), firstEntry.contestId(), firstEntry.artist(), firstEntry.title(),
                firstEntry.youtubeUrl(), firstEntry.comment(), firstEntry.assessment(), firstEntry.assessmentConfidence(), 16,
                firstEntry.rankingPosition(), firstEntry.contestParticipationId(), firstEntry.createdAt(), firstEntry.updatedAt()
        );
        List<ExportFormat.FullExport> malformed = List.of(
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), valid.exportedAt(), valid.applicationVersion(), valid.schemaVersion(),
                        dataWith(data, data.contests(), java.util.stream.Stream.concat(java.util.stream.Stream.of(poolGap), data.contestEntries().stream().skip(1)).toList(),
                                data.contestParticipations(), data.ballotSnapshots(), data.ballotSnapshotItems(), data.legacyReceivedScores())),
                new ExportFormat.FullExport(valid.format(), valid.formatVersion(), valid.exportedAt(), valid.applicationVersion(), valid.schemaVersion(),
                        dataWith(data, data.contests(), data.contestEntries(), data.contestParticipations(), List.of(), List.of(), data.legacyReceivedScores()))
        );

        ObjectMapper mapper = new ObjectMapper();
        for (ExportFormat.FullExport invalid : malformed) {
            assertThatThrownBy(() -> restores.previewUploadedJson(
                    new ByteArrayInputStream(mapper.writeValueAsBytes(invalid)), "malformed-v4.json"))
                    .isInstanceOf(BackupFileException.class);
        }
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Live");
    }

    @Test
    void appliesTheSameClosedBallotInvariantToAllLegacyJsonFormatsAfterUpgrade() throws Exception {
        insertFullData("Legacy lifecycle");
        ExportFormat.FullExport current = exports.snapshot();
        ExportFormat.Data data = current.data();
        ExportFormat.DataV1 v1Data = new ExportFormat.DataV1(
                legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), legacyEntriesV1(data),
                List.of(), List.of(), legacyScores(data)
        );
        ExportFormat.DataV2 v2Data = new ExportFormat.DataV2(
                legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), legacyEntriesV2(data),
                List.of(), List.of(), legacyScores(data)
        );
        ExportFormat.DataV3 v3Data = new ExportFormat.DataV3(
                legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), legacyEntriesV3(data),
                List.of(), List.of(), legacyScores(data)
        );
        List<Object> malformedLegacyExports = List.of(
                new ExportFormat.FullExportV1(ExportFormat.FORMAT, ExportFormat.LEGACY_VERSION, current.exportedAt(), current.applicationVersion(), current.schemaVersion(), v1Data),
                new ExportFormat.FullExportV2(ExportFormat.FORMAT, ExportFormat.VERSION_2, current.exportedAt(), current.applicationVersion(), current.schemaVersion(), v2Data),
                new ExportFormat.FullExportV3(ExportFormat.FORMAT, ExportFormat.VERSION_3, current.exportedAt(), current.applicationVersion(), current.schemaVersion(), v3Data)
        );

        ObjectMapper mapper = new ObjectMapper();
        for (Object invalid : malformedLegacyExports) {
            assertThatThrownBy(() -> restores.previewUploadedJson(
                    new ByteArrayInputStream(mapper.writeValueAsBytes(invalid)), "malformed-legacy.json"))
                    .isInstanceOf(BackupFileException.class);
        }
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Legacy lifecycle");
    }

    @Test
    void exportsOneConsistentSnapshotAndBlocksTheRestoreSwitchUntilItIsFinished() throws Exception {
        BackupSummary emptyState = backups.create(BackupReason.MANUAL);
        jdbc.update("""
                INSERT INTO candidate (motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at)
                VALUES (1,'Snapshot','Titel','https://www.youtube.com/watch?v=dQw4w9WgXcQ','Vor Snapshot','OFFEN',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        RestorePreview preview = restores.previewKnownBackup(emptyState.id());
        CountDownLatch snapshotEstablished = new CountDownLatch(1);
        CountDownLatch releaseSnapshot = new CountDownLatch(1);
        ExportService consistentExporter = new ExportService(dataSource, new ObjectMapper(), backups,
                new de.venomenon.cscxtool.participant.CountryCatalog(new ObjectMapper()), () -> {
                    snapshotEstablished.countDown();
                    try {
                        if (!releaseSnapshot.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("snapshot release timed out");
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
        CompletableFuture<ExportFormat.FullExport> exported = CompletableFuture.supplyAsync(consistentExporter::snapshot);
        assertThat(snapshotEstablished.await(10, TimeUnit.SECONDS)).isTrue();
        jdbc.update("UPDATE candidate SET comment = 'Nach Snapshot' WHERE motto_show_id = 1");
        CompletableFuture<RestoreResult> restored = CompletableFuture.supplyAsync(() -> restores.restore(preview.token()));
        Thread.sleep(150);
        assertThat(restored).isNotDone();

        releaseSnapshot.countDown();
        ExportFormat.FullExport snapshot = exported.get(10, TimeUnit.SECONDS);
        restored.get(10, TimeUnit.SECONDS);

        assertThat(snapshot.data().candidates()).extracting(ExportFormat.Candidate::comment).containsExactly("Vor Snapshot");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidate", Integer.class)).isZero();
    }

    @Test
    void blocksAConcurrentRestoreUntilOutstandingDatabaseReadHasFinished() throws Exception {
        insertFullData("Alt");
        BackupSummary backup = backups.create(BackupReason.MANUAL);
        jdbc.update("UPDATE candidate SET comment = 'Neu'");
        RestorePreview preview = restores.previewKnownBackup(backup.id());
        lock.acquireRead();
        CompletableFuture<RestoreResult> future;
        try {
            future = CompletableFuture.supplyAsync(() -> restores.restore(preview.token()));
            Thread.sleep(150);
            assertThat(future).isNotDone();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        } finally {
            lock.releaseRead();
        }
        try {
            assertThat(future.get(10, TimeUnit.SECONDS).message()).contains("vollständig");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Alt");
    }

    @Test
    void emitsExcelFriendlyCsvWithQuotesAndLineBreaks() {
        insertFullData("Ä; \"Zitat\"\nZeile");
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (12,'Aktiv',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (12,1,12,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        CsvExportService csv = new CsvExportService(dataSource, new de.venomenon.cscxtool.participant.CountryCatalog(new ObjectMapper()));
        String exported = new String(csv.candidates(), StandardCharsets.UTF_8);
        assertThat(exported).startsWith("\uFEFFCSC-Ausgabe;Show;Interpret");
        exported = exported.replaceFirst("^\\uFEFFCSC-Ausgabe;", "\uFEFF");
        assertThat(exported).startsWith("\uFEFFShow;Interpret").contains("\"Ä; \"\"Zitat\"\"\nZeile\"").contains("\r\n");
        assertThat(new String(csv.contestEntries(), StandardCharsets.UTF_8))
                .contains("Einschätzung (1–5);Sicherheit (1–5)", ";3;1;")
                .doesNotContain("Gehört", "Wiedervorlage");
        assertThat(new String(csv.results(), StandardCharsets.UTF_8))
                .contains("Abgeleitete Punkte")
                .doesNotContain("Aktiv;UNBEKANNT;");
    }

    @Test
    void createsPreMigrationAndMandatoryStartupBackupsAroundAPendingMigration() throws Exception {
        Path oldSchema = temporaryDirectory.resolve("old-schema.db");
        DataSource oldDataSource = SqliteDataSourceFactory.create(oldSchema);
        migrate(oldDataSource, "classpath:/db/changelog/p5-master.yaml");
        ApplicationStorage lifecycleStorage = storageFor(temporaryDirectory.resolve("lifecycle"));
        Files.createDirectories(lifecycleStorage.dataDirectory());
        new SqliteOnlineBackupAdapter().backup(oldDataSource, lifecycleStorage.databaseFile());
        DataSource lifecycleDataSource = new LockedDataSource(SqliteDataSourceFactory.create(lifecycleStorage.databaseFile()), new DatabaseAccessLock());
        BackupService lifecycleBackups = new BackupService(lifecycleStorage, lifecycleDataSource, new SqliteOnlineBackupAdapter(), new ObjectMapper());

        new LiquibaseStartupCoordinator(lifecycleDataSource, new DatabaseStartupState(true), lifecycleBackups, new SchemaMigrationProbe()).migrateAndBackUp();

        assertThat(lifecycleBackups.overview().automaticBackups()).extracting(BackupSummary::reason)
                .containsExactlyInAnyOrder(BackupReason.PRE_MIGRATION, BackupReason.STARTUP);
        assertThat(SchemaSupport.schemaVersion(lifecycleDataSource)).isEqualTo(SchemaSupport.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void firstStartupWithAnExistingEmptySqliteFileSkipsThePreMigrationBackup() throws Exception {
        ApplicationStorage freshStorage = storageFor(temporaryDirectory.resolve("fresh-startup"));
        Files.createDirectories(freshStorage.dataDirectory());
        Files.createFile(freshStorage.databaseFile());
        DatabaseStartupState startupState = DatabaseStartupState.inspect(freshStorage.databaseFile());
        assertThat(startupState.containsExistingSchema()).isFalse();
        DataSource freshDataSource = new LockedDataSource(SqliteDataSourceFactory.create(freshStorage.databaseFile()), new DatabaseAccessLock());
        BackupService freshBackups = new BackupService(freshStorage, freshDataSource, new SqliteOnlineBackupAdapter(), new ObjectMapper());

        new LiquibaseStartupCoordinator(freshDataSource, startupState, freshBackups, new SchemaMigrationProbe()).migrateAndBackUp();

        assertThat(freshBackups.overview().automaticBackups()).extracting(BackupSummary::reason).containsExactly(BackupReason.STARTUP);
        assertThat(SchemaSupport.schemaVersion(freshDataSource)).isEqualTo(SchemaSupport.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void backupFailureBeforeMigrationLeavesTheOldSchemaUntouched() throws Exception {
        Path oldSchema = temporaryDirectory.resolve("failed-migration.db");
        DataSource oldDataSource = SqliteDataSourceFactory.create(oldSchema);
        migrate(oldDataSource, "classpath:/db/changelog/p5-master.yaml");
        ApplicationStorage lifecycleStorage = storageFor(temporaryDirectory.resolve("failed-lifecycle"));
        Files.createDirectories(lifecycleStorage.dataDirectory());
        new SqliteOnlineBackupAdapter().backup(oldDataSource, lifecycleStorage.databaseFile());
        DataSource lifecycleDataSource = new LockedDataSource(SqliteDataSourceFactory.create(lifecycleStorage.databaseFile()), new DatabaseAccessLock());
        BackupService lifecycleBackups = new BackupService(lifecycleStorage, lifecycleDataSource, new FailingBackupAdapter(), new ObjectMapper());

        assertThatThrownBy(() -> new LiquibaseStartupCoordinator(lifecycleDataSource, new DatabaseStartupState(true), lifecycleBackups, new SchemaMigrationProbe()).migrateAndBackUp())
                .isInstanceOf(BackupStorageException.class);
        assertThat(SchemaSupport.schemaVersion(lifecycleDataSource)).isEqualTo(6);
        assertThat(lifecycleBackups.overview().automaticBackups()).isEmpty();
    }

    @Test
    void restoresTheSafetyBackupWhenATechnicalFailureOccursAfterTheLiveSwitch() {
        insertFullData("Sicher");
        BackupSummary old = backups.create(BackupReason.MANUAL);
        jdbc.update("UPDATE candidate SET comment = 'Aktuell'");
        RestoreService failingRestore = new RestoreService(storage, backups, exports, new FailingAfterFirstRestoreAdapter(), dataSource, lock);
        RestorePreview preview = failingRestore.previewKnownBackup(old.id());

        assertThatThrownBy(() -> failingRestore.restore(preview.token())).isInstanceOf(BackupStorageException.class);
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Aktuell");
    }

    @Test
    void reportsRecoveryFailureWithoutClaimingThatThePreviousStateIsKnown() {
        insertFullData("Sicher");
        BackupSummary old = backups.create(BackupReason.MANUAL);
        jdbc.update("UPDATE candidate SET comment = 'Aktuell'");
        RestoreService failingRestore = new RestoreService(storage, backups, exports, new FailingLiveAndRecoveryAdapter(), dataSource, lock);
        RestorePreview preview = failingRestore.previewKnownBackup(old.id());

        assertThatThrownBy(() -> failingRestore.restore(preview.token()))
                .isInstanceOf(RestoreRecoveryFailedException.class)
                .satisfies(exception -> assertThat(exception.getSuppressed()).isNotEmpty());
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Sicher");
    }

    @Test
    void rejectsTruncatedAndChecksumBrokenBackupArtifactsWithoutTouchingLiveData() throws Exception {
        insertFullData("Live");
        BackupSummary valid = backups.create(BackupReason.MANUAL);
        Path artifact = backups.resolveKnownArtifact(valid.id());
        byte[] bytes = Files.readAllBytes(artifact);
        assertThatThrownBy(() -> restores.previewUploadedBackup(new ByteArrayInputStream(java.util.Arrays.copyOf(bytes, 20)), "abgebrochen.cscbackup"))
                .isInstanceOfSatisfying(BackupFileException.class, exception -> assertThat(exception.code()).isEqualTo("BACKUP_INVALID"));

        Path checksumBroken = temporaryDirectory.resolve("checksum-broken.cscbackup");
        writeChecksumBrokenArtifact(artifact, checksumBroken);
        assertThatThrownBy(() -> restores.previewUploadedBackup(new ByteArrayInputStream(Files.readAllBytes(checksumBroken)), "checksum.cscbackup"))
                .isInstanceOfSatisfying(BackupFileException.class, exception -> assertThat(exception.code()).isEqualTo("BACKUP_CHECKSUM_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Live");
    }

    private static ExportFormat.Data dataWith(
            ExportFormat.Data data, List<ExportFormat.Contest> contests, List<ExportFormat.ContestEntry> entries,
            List<ExportFormat.ContestParticipation> participations, List<ExportFormat.BallotSnapshot> snapshots,
            List<ExportFormat.BallotSnapshotItem> snapshotItems, List<ExportFormat.LegacyReceivedScore> scores
    ) {
        return new ExportFormat.Data(contests, data.mottoShows(), data.candidates(), data.participants(), participations,
                data.participantAliases(), entries, snapshots, snapshotItems, data.legacyResults(), scores,
                data.publishedBallots(), data.publishedBallotPositions());
    }

    private static ExportFormat.DataV1 legacyDataV1(ExportFormat.Data data, List<ExportFormat.ContestEntryV1> entries) {
        return new ExportFormat.DataV1(
                legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), entries,
                data.ballotSnapshots(), data.ballotSnapshotItems(), legacyScores(data)
        );
    }

    private static ExportFormat.DataV2 legacyDataV2(ExportFormat.Data data, List<ExportFormat.ContestEntryV2> entries) {
        return new ExportFormat.DataV2(
                legacyShows(data), data.candidates(), legacyParticipants(data), data.participantAliases(), entries,
                data.ballotSnapshots(), data.ballotSnapshotItems(), legacyScores(data)
        );
    }

    private static List<ExportFormat.MottoShowV3> legacyShows(ExportFormat.Data data) {
        return data.mottoShows().stream().map(show -> {
            ExportFormat.LegacyResult legacy = data.legacyResults().stream()
                    .filter(value -> value.mottoShowId() == show.id()).findFirst().orElse(null);
            return new ExportFormat.MottoShowV3(
                    show.id(), show.showNumber(), show.name(), show.selectedCandidateId(), show.ballotClosedAt(),
                    legacy == null ? null : legacy.resultsClosedAt(), legacy == null ? null : legacy.finalPlace(),
                    legacy != null && legacy.finalPlaceTied(), legacy == null ? null : legacy.officialTotalPoints(), show.createdAt(), show.updatedAt()
            );
        }).toList();
    }

    private static List<ExportFormat.ContestV6> legacyContestsV6(ExportFormat.Data data) {
        return data.contests().stream().map(contest -> new ExportFormat.ContestV6(
                contest.id(), contest.name(), contest.displayOrder(), contest.current(), contest.createdAt(), contest.updatedAt()
        )).toList();
    }

    private static List<ExportFormat.MottoShowV6> legacyShowsV6(ExportFormat.Data data) {
        return data.mottoShows().stream().map(show -> {
            ExportFormat.LegacyResult legacy = data.legacyResults().stream()
                    .filter(value -> value.mottoShowId() == show.id()).findFirst().orElse(null);
            return new ExportFormat.MottoShowV6(
                    show.id(), show.contestId(), show.showNumber(), show.name(), show.entryListComplete(), show.selectedCandidateId(), show.ballotClosedAt(),
                    legacy == null ? null : legacy.resultsClosedAt(), legacy == null ? null : legacy.finalPlace(),
                    legacy != null && legacy.finalPlaceTied(), legacy == null ? null : legacy.officialTotalPoints(), show.createdAt(), show.updatedAt()
            );
        }).toList();
    }

    private static List<ExportFormat.ReceivedScore> legacyScoresV6(ExportFormat.Data data) {
        return data.legacyReceivedScores().stream().map(score -> new ExportFormat.ReceivedScore(
                score.id(), score.mottoShowId(), score.contestId(), score.contestParticipationId(), score.status(), score.points(),
                score.createdAt(), score.updatedAt()
        )).toList();
    }

    private static List<ExportFormat.ParticipantV3> legacyParticipants(ExportFormat.Data data) {
        return data.participants().stream().map(participant -> {
            ExportFormat.ContestParticipation participation = data.contestParticipations().stream()
                    .filter(value -> value.participantId() == participant.id()).findFirst().orElseThrow();
            return new ExportFormat.ParticipantV3(participant.id(), participant.displayName(), participation.countryCode(),
                    participant.active(), participant.createdAt(), participant.updatedAt());
        }).toList();
    }

    private static List<ExportFormat.ReceivedScoreV3> legacyScores(ExportFormat.Data data) {
        return data.legacyReceivedScores().stream().map(score -> {
            ExportFormat.ContestParticipation participation = data.contestParticipations().stream()
                    .filter(value -> value.id() == score.contestParticipationId()).findFirst().orElseThrow();
            return new ExportFormat.ReceivedScoreV3(score.id(), score.mottoShowId(), participation.participantId(),
                    score.status(), score.points(), score.createdAt(), score.updatedAt());
        }).toList();
    }

    private static List<ExportFormat.ContestEntryV1> legacyEntriesV1(ExportFormat.Data data) {
        return data.contestEntries().stream().map(entry -> new ExportFormat.ContestEntryV1(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                entry.id() != 200 && entry.id() != 201, entry.id() == 201 || entry.id() == 202,
                entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
    }

    private static List<ExportFormat.ContestEntryV2> legacyEntriesV2(ExportFormat.Data data) {
        return data.contestEntries().stream().map(entry -> new ExportFormat.ContestEntryV2(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                entry.id() != 200 && entry.id() != 201, entry.id() == 201 || entry.id() == 202,
                entry.poolPosition(), entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
    }

    private static List<ExportFormat.ContestEntryV3> legacyEntriesV3(ExportFormat.Data data) {
        return data.contestEntries().stream().map(entry -> new ExportFormat.ContestEntryV3(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                entry.assessment(), entry.assessmentConfidence(), entry.poolPosition(), entry.rankingPosition(),
                entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
    }

    private void insertFullData(String comment) {
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (10,'Inaktiv',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (10,1,10,'DE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO participant_alias (id,participant_id,alias) VALUES (11,10,'Alias')");
        jdbc.update("""
                INSERT INTO candidate (id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at)
                VALUES (100,1,'Künstler','Titel','https://www.youtube.com/watch?v=dQw4w9WgXcQ',?,'FINALIST',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, comment);
        jdbc.update("UPDATE motto_show SET selected_candidate_id=100,ballot_closed_at=CURRENT_TIMESTAMP WHERE id=1");
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("""
                    INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,comment,assessment,assessment_confidence,pool_position,ranking_position,contest_participation_id,created_at,updated_at)
                    VALUES (?,1,1,?,?,'https://www.youtube.com/watch?v=dQw4w9WgXcQ','Kommentar',3,1,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, 199 + rank, "Beitrag " + rank, "Song " + rank, rank, rank, rank == 1 ? 10 : null);
        }
        jdbc.update("INSERT INTO ballot_snapshot (id,motto_show_id,snapshot_number,created_at,is_current) VALUES (300,1,1,CURRENT_TIMESTAMP,1)");
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("""
                    INSERT INTO ballot_snapshot_item (id,ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot)
                    VALUES (?,300,?,?,?,?,'https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                    """, 399 + rank, rank, rank == 1 ? null : 199 + rank,
                    rank == 1 ? "Historisch" : "Beitrag " + rank, rank == 1 ? "Snapshot" : "Song " + rank);
        }
        jdbc.update("""
                INSERT INTO legacy_received_score (id,motto_show_id,contest_id,contest_participation_id,status,points,created_at,updated_at,archived_at)
                VALUES (500,1,1,10,'ABGESTIMMT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO legacy_result (id,motto_show_id,results_closed_at,final_place,final_place_tied,official_total_points,archived_at)
                VALUES (1,1,CURRENT_TIMESTAMP,1,1,0,CURRENT_TIMESTAMP)
                """);
    }

    private ApplicationStorage storageFor(Path root) throws Exception {
        Files.createDirectories(root.resolve("backups/automatic"));
        Files.createDirectories(root.resolve("backups/manual"));
        Files.createDirectories(root.resolve("exports"));
        Files.createDirectories(root.resolve("logs"));
        Files.createDirectories(root.resolve("runtime"));
        return new ApplicationStorage(root, root.resolve("data"), root.resolve("data/csc-x-tool.db"),
                root.resolve("backups/automatic"), root.resolve("backups/manual"), root.resolve("exports"),
                root.resolve("logs"), root.resolve("runtime"));
    }

    private static void writeChecksumBrokenArtifact(Path source, Path target) throws Exception {
        try (ZipFile input = new ZipFile(source.toFile()); ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target))) {
            var entries = input.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                output.putNextEntry(new ZipEntry(entry.getName()));
                try (var entryInput = input.getInputStream(entry)) {
                    entryInput.transferTo(output);
                }
                if ("database.sqlite".equals(entry.getName())) output.write(0);
                output.closeEntry();
            }
        }
    }

    private static void migrate(DataSource source, String changeLog) throws Exception {
        liquibase.integration.spring.SpringLiquibase liquibase = new liquibase.integration.spring.SpringLiquibase();
        liquibase.setDataSource(source);
        liquibase.setChangeLog(changeLog);
        liquibase.afterPropertiesSet();
    }

    private static final class FailingBackupAdapter extends SqliteOnlineBackupAdapter {
        @Override public void backup(DataSource source, Path target) throws java.sql.SQLException { throw new java.sql.SQLException("disk unavailable"); }
    }

    private static final class FailingAfterFirstRestoreAdapter extends SqliteOnlineBackupAdapter {
        private boolean fail = true;
        @Override public void restore(DataSource target, Path source) throws java.sql.SQLException {
            super.restore(target, source);
            if (fail) {
                fail = false;
                throw new java.sql.SQLException("simulated verification failure after live switch");
            }
        }
    }

    private static final class FailingLiveAndRecoveryAdapter extends SqliteOnlineBackupAdapter {
        private int calls;

        @Override public void restore(DataSource target, Path source) throws java.sql.SQLException {
            if (calls++ == 0) {
                super.restore(target, source);
                throw new java.sql.SQLException("live restore verification failed");
            }
            throw new java.sql.SQLException("safety backup recovery failed");
        }
    }
}
