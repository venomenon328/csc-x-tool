package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.system.ApplicationStorage;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RestoreService {

    private static final Duration PREVIEW_TTL = Duration.ofMinutes(30);

    private final ApplicationStorage storage;
    private final BackupService backups;
    private final ExportService exports;
    private final SqliteOnlineBackupAdapter sqliteBackup;
    private final DataSource dataSource;
    private final DatabaseAccessLock accessLock;
    private final Map<String, PreparedRestore> prepared = new ConcurrentHashMap<>();

    public RestoreService(
            ApplicationStorage storage, BackupService backups, ExportService exports,
            SqliteOnlineBackupAdapter sqliteBackup, DataSource dataSource, DatabaseAccessLock accessLock
    ) {
        this.storage = storage;
        this.backups = backups;
        this.exports = exports;
        this.sqliteBackup = sqliteBackup;
        this.dataSource = dataSource;
        this.accessLock = accessLock;
    }

    public RestorePreview previewKnownBackup(String id) {
        cleanupExpiredPreviews();
        Path artifact = backups.resolveKnownArtifact(id);
        BackupManifest manifest = backups.readManifest(artifact);
        Path work = newWorkDirectory();
        try {
            Path stage = backups.extractVerifiedSnapshot(artifact, work);
            migrateAndVerify(stage);
            return retain(new PreparedRestore(work, stage, "Sicherung", artifact.getFileName().toString(),
                    manifest.createdAt().toString(), manifest.applicationVersion(), manifest.schemaVersion()));
        } catch (RuntimeException exception) {
            BackupService.deleteRecursively(work);
            throw exception;
        }
    }

    public RestorePreview previewUploadedBackup(InputStream input, String originalFilename) {
        cleanupExpiredPreviews();
        Path work = newWorkDirectory();
        try {
            Path artifact = work.resolve("uploaded.cscbackup");
            copyLimited(input, artifact);
            BackupManifest manifest = backups.readManifest(artifact);
            Path stage = backups.extractVerifiedSnapshot(artifact, work);
            migrateAndVerify(stage);
            return retain(new PreparedRestore(work, stage, "Sicherung", safeName(originalFilename),
                    manifest.createdAt().toString(), manifest.applicationVersion(), manifest.schemaVersion()));
        } catch (RuntimeException exception) {
            BackupService.deleteRecursively(work);
            throw exception;
        }
    }

    public RestorePreview previewUploadedJson(InputStream input, String originalFilename) {
        cleanupExpiredPreviews();
        Path work = newWorkDirectory();
        try {
            Path exportFile = work.resolve("uploaded.json");
            copyLimited(input, exportFile);
            ExportFormat.FullExport export = exports.readAndValidate(exportFile);
            Path stage = work.resolve("stage.sqlite");
            exports.restoreInto(stage, export);
            return retain(new PreparedRestore(work, stage, "JSON-Export", safeName(originalFilename), export.exportedAt(),
                    export.applicationVersion(), export.schemaVersion()));
        } catch (RuntimeException exception) {
            BackupService.deleteRecursively(work);
            throw exception;
        }
    }

    public RestoreResult restore(String token) {
        cleanupExpiredPreviews();
        PreparedRestore restore = prepared.remove(token);
        if (restore == null) {
            throw new BackupFileException("RESTORE_PREVIEW_EXPIRED", "Die Wiederherstellungsvorschau ist nicht mehr verf\u00fcgbar. Bitte pr\u00fcfen Sie die Datei erneut.");
        }
        try {
            return accessLock.withExclusive(() -> restoreExclusively(restore));
        } finally {
            BackupService.deleteRecursively(restore.workDirectory());
        }
    }

    private RestoreResult restoreExclusively(PreparedRestore restore) {
        BackupSummary safetyBackup = backups.create(BackupReason.PRE_RESTORE);
        try {
            sqliteBackup.restore(dataSource, restore.stageDatabase());
            verifyLiveDatabase();
            return new RestoreResult("Die Daten wurden vollst\u00e4ndig wiederhergestellt.", safetyBackup);
        } catch (Exception restoreFailure) {
            try {
                Path recovery = newWorkDirectory();
                try {
                    Path snapshot = backups.extractVerifiedSnapshot(backups.resolveKnownArtifact(safetyBackup.id()), recovery);
                    sqliteBackup.restore(dataSource, snapshot);
                    verifyLiveDatabase();
                } finally {
                    BackupService.deleteRecursively(recovery);
                }
            } catch (Exception recoveryFailure) {
                throw new RestoreRecoveryFailedException(restoreFailure, recoveryFailure);
            }
            throw new BackupStorageException("Die Wiederherstellung ist technisch fehlgeschlagen; der vorherige Stand wurde zur\u00fcckgespielt.", restoreFailure);
        }
    }

    private RestorePreview retain(PreparedRestore restore) {
        String token = UUID.randomUUID().toString();
        prepared.put(token, restore.withPreparedAt(Instant.now()));
        return new RestorePreview(token, restore.sourceType(), restore.sourceName(), restore.createdAt(),
                restore.applicationVersion(), restore.schemaVersion(), true, counts(restore.stageDatabase()));
    }

    private void migrateAndVerify(Path stage) {
        try {
            SchemaSupport.migrate(stage);
        } catch (Exception exception) {
            throw new BackupStorageException("Die Staging-Sicherung konnte technisch nicht auf die aktuelle Datenbankschemaversion migriert werden.", exception);
        }
        try {
            SchemaSupport.verify(stage, SchemaSupport.CURRENT_SCHEMA_VERSION);
        } catch (BackupFileException exception) {
            throw new BackupStorageException("Die migrierte Staging-Sicherung besteht die SQLite-Prüfung nicht.", exception);
        }
    }

    private void verifyLiveDatabase() {
        SchemaSupport.verify(storage.databaseFile(), SchemaSupport.CURRENT_SCHEMA_VERSION);
        int version = SchemaSupport.schemaVersion(dataSource);
        if (version != SchemaSupport.CURRENT_SCHEMA_VERSION) {
            throw new BackupStorageException("Die wiederhergestellte Datenbank besitzt nicht die erwartete Schemaversion.", null);
        }
    }

    private RestoreDataCounts counts(Path database) {
        JdbcTemplate jdbc = new JdbcTemplate(SqliteDataSourceFactory.create(database));
        return new RestoreDataCounts(
                count(jdbc, "motto_show"), count(jdbc, "candidate"), count(jdbc, "participant"),
                count(jdbc, "contest_entry"), count(jdbc, "participant_botb_selection"), count(jdbc, "ballot_snapshot"),
                count(jdbc, "legacy_received_score")
        );
    }

    private static int count(JdbcTemplate jdbc, String table) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private Path newWorkDirectory() {
        try {
            return Files.createTempDirectory(storage.runtimeDirectory(), "restore-");
        } catch (IOException exception) {
            throw new BackupStorageException("Ein tempor\u00e4rer Arbeitsbereich f\u00fcr die Wiederherstellung konnte nicht erstellt werden.", exception);
        }
    }

    private void cleanupExpiredPreviews() {
        Instant cutoff = Instant.now().minus(PREVIEW_TTL);
        prepared.entrySet().removeIf(entry -> {
            PreparedRestore preview = entry.getValue();
            if (preview.preparedAt() != null && preview.preparedAt().isBefore(cutoff)) {
                BackupService.deleteRecursively(preview.workDirectory());
                return true;
            }
            return false;
        });
    }

    private static void copyLimited(InputStream input, Path target) {
        long size = 0;
        byte[] buffer = new byte[8192];
        try (input; var output = Files.newOutputStream(target)) {
            for (int read; (read = input.read(buffer)) >= 0;) {
                size += read;
                if (size > 128L * 1024 * 1024) {
                    throw new BackupFileException("RESTORE_FILE_TOO_LARGE", "Die Wiederherstellungsdatei ist zu gro\u00df.");
                }
                output.write(buffer, 0, read);
            }
        } catch (BackupFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BackupFileException("RESTORE_FILE_UNREADABLE", "Die Wiederherstellungsdatei kann nicht gelesen werden.", exception);
        }
    }

    private static String safeName(String name) {
        return name == null || name.isBlank() ? "hochgeladene Datei" : Path.of(name).getFileName().toString();
    }

    private record PreparedRestore(Path workDirectory, Path stageDatabase, String sourceType, String sourceName,
                                   String createdAt, String applicationVersion, int schemaVersion, Instant preparedAt) {
        PreparedRestore(Path workDirectory, Path stageDatabase, String sourceType, String sourceName,
                        String createdAt, String applicationVersion, int schemaVersion) {
            this(workDirectory, stageDatabase, sourceType, sourceName, createdAt, applicationVersion, schemaVersion, null);
        }

        PreparedRestore withPreparedAt(Instant value) {
            return new PreparedRestore(workDirectory, stageDatabase, sourceType, sourceName, createdAt,
                    applicationVersion, schemaVersion, value);
        }
    }
}
