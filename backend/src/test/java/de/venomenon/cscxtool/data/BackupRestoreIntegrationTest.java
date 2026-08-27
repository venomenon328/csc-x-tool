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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    void roundTripsAllBusinessTablesViaJsonAndLeavesSequencesUsable() throws Exception {
        insertFullData("Ä; \"Zitat\"\nZeile");
        byte[] json = exports.exportJson();
        jdbc.update("DELETE FROM received_score");
        jdbc.update("DELETE FROM ballot_snapshot_item");
        jdbc.update("DELETE FROM ballot_snapshot");
        jdbc.update("DELETE FROM contest_entry");
        jdbc.update("UPDATE motto_show SET selected_candidate_id = NULL");
        jdbc.update("DELETE FROM candidate");
        jdbc.update("DELETE FROM participant_alias");
        jdbc.update("DELETE FROM participant");

        RestorePreview preview = restores.previewUploadedJson(new ByteArrayInputStream(json), "voll.json");
        assertThat(preview.counts()).extracting(RestoreDataCounts::candidates, RestoreDataCounts::participants,
                RestoreDataCounts::contestEntries, RestoreDataCounts::ballotSnapshots, RestoreDataCounts::receivedScores)
                .containsExactly(1, 1, 1, 1, 1);
        restores.restore(preview.token());

        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Ä; \"Zitat\"\nZeile");
        assertThat(jdbc.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE id = 400", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT points FROM received_score WHERE id = 500", Integer.class)).isZero();
        jdbc.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (1, 'Neu', 'Neu', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'OFFEN', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThat(jdbc.queryForObject("SELECT MAX(id) FROM candidate", Long.class)).isGreaterThan(100L);
    }

    @Test
    void rejectsUnknownNewerJsonWithoutChangingLiveDatabase() throws Exception {
        insertFullData("Live");
        String newer = new String(exports.exportJson(), StandardCharsets.UTF_8).replace("\"formatVersion\":1", "\"formatVersion\":2");
        assertThatThrownBy(() -> restores.previewUploadedJson(new ByteArrayInputStream(newer.getBytes(StandardCharsets.UTF_8)), "new.json"))
                .isInstanceOf(BackupFileException.class).hasMessageContaining("nicht unterst");
        assertThat(jdbc.queryForObject("SELECT comment FROM candidate WHERE id = 100", String.class)).isEqualTo("Live");
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
        CsvExportService csv = new CsvExportService(dataSource, new de.venomenon.cscxtool.participant.CountryCatalog(new ObjectMapper()));
        String exported = new String(csv.candidates(), StandardCharsets.UTF_8);
        assertThat(exported).startsWith("\uFEFFShow;Interpret").contains("\"Ä; \"\"Zitat\"\"\nZeile\"").contains("\r\n");
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
        assertThat(SchemaSupport.schemaVersion(lifecycleDataSource)).isEqualTo(7);
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

    private void insertFullData(String comment) {
        jdbc.update("INSERT INTO participant (id,display_name,country_code,active,created_at,updated_at) VALUES (10,'Inaktiv','DE',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO participant_alias (id,participant_id,alias) VALUES (11,10,'Alias')");
        jdbc.update("""
                INSERT INTO candidate (id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at)
                VALUES (100,1,'Künstler','Titel','https://www.youtube.com/watch?v=dQw4w9WgXcQ',?,'FINALIST',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, comment);
        jdbc.update("UPDATE motto_show SET selected_candidate_id=100,ballot_closed_at=CURRENT_TIMESTAMP,results_closed_at=CURRENT_TIMESTAMP,official_total_points=0,final_place=1,final_place_tied=1 WHERE id=1");
        jdbc.update("""
                INSERT INTO contest_entry (id,motto_show_id,artist,title,youtube_url,comment,listened,relisten,ranking_position,participant_id,created_at,updated_at)
                VALUES (200,1,'Beitrag','Song','https://www.youtube.com/watch?v=dQw4w9WgXcQ','Kommentar',1,1,1,10,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("INSERT INTO ballot_snapshot (id,motto_show_id,snapshot_number,created_at,is_current) VALUES (300,1,1,CURRENT_TIMESTAMP,1)");
        jdbc.update("""
                INSERT INTO ballot_snapshot_item (id,ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot)
                VALUES (400,300,1,NULL,'Historisch','Snapshot','https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                """);
        jdbc.update("""
                INSERT INTO received_score (id,motto_show_id,participant_id,status,points,created_at,updated_at)
                VALUES (500,1,10,'ABGESTIMMT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
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
}
