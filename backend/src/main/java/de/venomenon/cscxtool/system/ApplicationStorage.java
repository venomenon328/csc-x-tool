package de.venomenon.cscxtool.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record ApplicationStorage(
        Path root,
        Path dataDirectory,
        Path databaseFile,
        Path automaticBackupsDirectory,
        Path manualBackupsDirectory,
        Path exportsDirectory,
        Path logsDirectory,
        Path runtimeDirectory
) {

    static ApplicationStorage prepare(Path configuredRoot) {
        return prepare(configuredRoot, ApplicationStorage::verifyWritable);
    }

    static ApplicationStorage prepare(Path configuredRoot, DirectoryWriteProbe writeProbe) {
        Path root = resolveRoot(configuredRoot);
        Path dataDirectory = root.resolve("data");
        Path automaticBackupsDirectory = root.resolve("backups").resolve("automatic");
        Path manualBackupsDirectory = root.resolve("backups").resolve("manual");
        Path exportsDirectory = root.resolve("exports");
        Path logsDirectory = root.resolve("logs");
        Path runtimeDirectory = root.resolve("runtime");

        List.of(
                dataDirectory,
                automaticBackupsDirectory,
                manualBackupsDirectory,
                exportsDirectory,
                logsDirectory,
                runtimeDirectory
        ).forEach(directory -> prepareDirectory(directory, writeProbe));

        return new ApplicationStorage(
                root,
                dataDirectory,
                dataDirectory.resolve("csc-x-tool.db"),
                automaticBackupsDirectory,
                manualBackupsDirectory,
                exportsDirectory,
                logsDirectory,
                runtimeDirectory
        );
    }

    private static Path resolveRoot(Path configuredRoot) {
        if (configuredRoot != null) {
            return configuredRoot.toAbsolutePath().normalize();
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            throw new StorageInitializationException(
                    "Kein Storage Root konfiguriert: Setzen Sie 'csc-x-tool.storage.root', "
                            + "weil die Umgebungsvariable LOCALAPPDATA nicht verfügbar ist.",
                    null
            );
        }
        return Path.of(localAppData, "CSC-X-Tool").toAbsolutePath().normalize();
    }

    private static void prepareDirectory(Path directory, DirectoryWriteProbe writeProbe) {
        try {
            Files.createDirectories(directory);
            writeProbe.verify(directory);
        } catch (IOException | SecurityException exception) {
            throw new StorageInitializationException(
                    "Das Anwendungsverzeichnis '" + directory + "' kann nicht verwendet werden.",
                    exception
            );
        }
    }

    private static void verifyWritable(Path directory) throws IOException {
        Path probe = Files.createTempFile(directory, ".csc-x-tool-write-", ".probe");
        Files.deleteIfExists(probe);
    }

    @FunctionalInterface
    interface DirectoryWriteProbe {

        void verify(Path directory) throws IOException;
    }
}
