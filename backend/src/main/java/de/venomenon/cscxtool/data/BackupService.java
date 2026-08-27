package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.system.ApplicationStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class BackupService {

    private static final String MANIFEST_ENTRY = "manifest.json";
    private static final String DATABASE_ENTRY = "database.sqlite";
    private static final int AUTOMATIC_RETENTION = 30;

    private final ApplicationStorage storage;
    private final DataSource dataSource;
    private final SqliteOnlineBackupAdapter sqliteBackup;
    private final ObjectMapper objectMapper;
    private final String applicationVersion;

    @Autowired
    public BackupService(
            ApplicationStorage storage,
            DataSource dataSource,
            SqliteOnlineBackupAdapter sqliteBackup,
            ObjectMapper objectMapper,
            ObjectProvider<BuildProperties> buildProperties
    ) {
        this.storage = storage;
        this.dataSource = dataSource;
        this.sqliteBackup = sqliteBackup;
        this.objectMapper = objectMapper;
        BuildProperties properties = buildProperties.getIfAvailable();
        this.applicationVersion = properties == null ? packageBuildVersion() : properties.getVersion();
    }

    BackupService(ApplicationStorage storage, DataSource dataSource, SqliteOnlineBackupAdapter sqliteBackup, ObjectMapper objectMapper) {
        this.storage = storage;
        this.dataSource = dataSource;
        this.sqliteBackup = sqliteBackup;
        this.objectMapper = objectMapper;
        this.applicationVersion = "test-build";
    }

    public BackupSummary create(BackupReason reason) {
        Path directory = reason.automatic() ? storage.automaticBackupsDirectory() : storage.manualBackupsDirectory();
        cleanupPartialArtifacts(directory);
        Instant now = Instant.now();
        String fileName = "backup-" + DateTimeFormatter.ISO_INSTANT.format(now).replace(":", "-")
                + "-" + UUID.randomUUID() + ".cscbackup";
        Path target = directory.resolve(fileName);
        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory(directory, ".backup-writing-");
            Path snapshot = temporaryDirectory.resolve(DATABASE_ENTRY);
            sqliteBackup.backup(dataSource, snapshot);
            verifyCreatedSnapshot(snapshot);
            BackupManifest manifest = new BackupManifest(
                    BackupManifest.FORMAT_VERSION, now, applicationVersion,
                    SchemaSupport.schemaVersion(dataSource), reason, sha256(snapshot)
            );
            Path part = temporaryDirectory.resolve(fileName + ".part");
            writeContainer(part, manifest, snapshot);
            publish(part, target);
            BackupSummary summary = summary(target, manifest);
            if (reason.automatic()) {
                retainAutomaticBackups();
            }
            return summary;
        } catch (Exception exception) {
            throw new BackupStorageException("Eine Sicherung konnte nicht im Anwendungsverzeichnis erstellt werden.", exception);
        } finally {
            deleteRecursively(temporaryDirectory);
        }
    }

    public BackupOverview overview() {
        List<BackupSummary> automatic = list(storage.automaticBackupsDirectory());
        List<BackupSummary> manual = list(storage.manualBackupsDirectory());
        BackupSummary latest = java.util.stream.Stream.concat(automatic.stream(), manual.stream())
                .max(Comparator.comparing(BackupSummary::createdAt)).orElse(null);
        return BackupOverview.of(
                storage.databaseFile(), storage.automaticBackupsDirectory(), storage.manualBackupsDirectory(),
                storage.exportsDirectory(), latest, automatic, manual
        );
    }

    public Path resolveKnownArtifact(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+\\.cscbackup")) {
            throw new BackupFileException("BACKUP_NOT_FOUND", "Die angeforderte Sicherung wurde nicht gefunden.");
        }
        for (Path directory : List.of(storage.automaticBackupsDirectory(), storage.manualBackupsDirectory())) {
            Path candidate = directory.resolve(id).normalize();
            if (candidate.getParent().equals(directory) && Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new BackupFileException("BACKUP_NOT_FOUND", "Die angeforderte Sicherung wurde nicht gefunden.");
    }

    BackupManifest readManifest(Path artifact) {
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            ZipEntry entry = zip.getEntry(MANIFEST_ENTRY);
            if (entry == null || entry.getSize() > 64 * 1024) {
                throw new BackupFileException("BACKUP_INVALID", "Die Sicherung enth\u00e4lt kein g\u00fcltiges Manifest.");
            }
            try (InputStream input = zip.getInputStream(entry)) {
                BackupManifest manifest = objectMapper.readValue(input, BackupManifest.class);
                if (manifest.backupFormatVersion() != BackupManifest.FORMAT_VERSION
                        || manifest.createdAt() == null || manifest.reason() == null
                        || manifest.databaseSha256() == null || !manifest.databaseSha256().matches("[0-9a-f]{64}")) {
                    throw new BackupFileException("BACKUP_INVALID", "Die Sicherung enth\u00e4lt ein ung\u00fcltiges Manifest.");
                }
                if (manifest.schemaVersion() > SchemaSupport.CURRENT_SCHEMA_VERSION) {
                    throw new BackupFileException("BACKUP_SCHEMA_TOO_NEW", "Die Sicherung wurde mit einer neueren Datenbankschemaversion erstellt.");
                }
                return manifest;
            }
        } catch (BackupFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupFileException("BACKUP_INVALID", "Die Sicherungsdatei ist nicht lesbar.", exception);
        }
    }

    Path extractVerifiedSnapshot(Path artifact, Path workDirectory) {
        BackupManifest manifest = readManifest(artifact);
        Path snapshot = workDirectory.resolve(DATABASE_ENTRY);
        try (ZipFile zip = new ZipFile(artifact.toFile())) {
            ZipEntry entry = zip.getEntry(DATABASE_ENTRY);
            if (entry == null || entry.isDirectory() || entry.getSize() > 1024L * 1024 * 1024) {
                throw new BackupFileException("BACKUP_INVALID", "Die Sicherung enth\u00e4lt keinen g\u00fcltigen Datenbanksnapshot.");
            }
            try (InputStream input = zip.getInputStream(entry); OutputStream output = Files.newOutputStream(snapshot)) {
                input.transferTo(output);
            }
            if (!sha256(snapshot).equals(manifest.databaseSha256())) {
                throw new BackupFileException("BACKUP_CHECKSUM_MISMATCH", "Die Pr\u00fcfsumme der Sicherung stimmt nicht mit dem Manifest \u00fcberein.");
            }
            SchemaSupport.verify(snapshot, SchemaSupport.CURRENT_SCHEMA_VERSION);
            return snapshot;
        } catch (BackupFileException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new BackupFileException("BACKUP_INVALID", "Die Sicherungsdatei kann nicht gelesen werden.", exception);
        }
    }

    private List<BackupSummary> list(Path directory) {
        cleanupPartialArtifacts(directory);
        try (var files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".cscbackup"))
                    .map(this::safeSummary).filter(java.util.Objects::nonNull)
                    .sorted(Comparator.comparing(BackupSummary::createdAt).reversed()).toList();
        } catch (IOException exception) {
            throw new BackupStorageException("Das Sicherungsverzeichnis kann nicht gelesen werden.", exception);
        }
    }

    private BackupSummary safeSummary(Path path) {
        try { return summary(path, readManifest(path)); } catch (BackupFileException exception) { return null; }
    }

    private BackupSummary summary(Path path, BackupManifest manifest) {
        try {
            return new BackupSummary(path.getFileName().toString(), manifest.createdAt(), manifest.applicationVersion(),
                    manifest.schemaVersion(), manifest.reason(), Files.size(path));
        } catch (IOException exception) {
            throw new BackupStorageException("Die Sicherungsmetadaten k\u00f6nnen nicht gelesen werden.", exception);
        }
    }

    private void retainAutomaticBackups() {
        List<BackupSummary> backups = list(storage.automaticBackupsDirectory());
        for (BackupSummary backup : backups.stream().skip(AUTOMATIC_RETENTION).toList()) {
            try {
                Files.deleteIfExists(storage.automaticBackupsDirectory().resolve(backup.id()));
            } catch (IOException exception) {
                throw new BackupStorageException("Eine alte automatische Sicherung konnte nicht bereinigt werden.", exception);
            }
        }
    }

    private void writeContainer(Path target, BackupManifest manifest, Path snapshot) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            zip.putNextEntry(new ZipEntry(MANIFEST_ENTRY));
            zip.write(objectMapper.writeValueAsBytes(manifest));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(DATABASE_ENTRY));
            Files.copy(snapshot, zip);
            zip.closeEntry();
        }
    }

    private void verifyCreatedSnapshot(Path snapshot) {
        try {
            SchemaSupport.verify(snapshot, SchemaSupport.CURRENT_SCHEMA_VERSION);
        } catch (BackupFileException exception) {
            throw new BackupStorageException("Der erzeugte Sicherungssnapshot ist nicht lesbar.", exception);
        }
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int read; (read = input.read(buffer)) >= 0;) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 ist in dieser Java-Laufzeit nicht verf\u00fcgbar.", exception);
        }
    }

    private static void publish(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(part, target);
        }
    }

    private static void cleanupPartialArtifacts(Path directory) {
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().contains(".part") || path.getFileName().toString().startsWith(".backup-writing-"))
                    .forEach(BackupService::deleteRecursively);
        } catch (IOException ignored) {
            // Directory usability has already been checked; a later real write reports the actual problem.
        }
    }

    static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) return;
        try (var files = Files.walk(path)) {
            files.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try { Files.deleteIfExists(candidate); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }

    String applicationVersion() {
        return applicationVersion;
    }

    private static String packageBuildVersion() {
        String value = BackupService.class.getPackage().getImplementationVersion();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Die Build-Metadaten für Sicherungen sind nicht verfügbar.");
        }
        return value;
    }
}
