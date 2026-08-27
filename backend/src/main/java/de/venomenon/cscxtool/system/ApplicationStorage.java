package de.venomenon.cscxtool.system;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        Path root = resolveRoot(configuredRoot);
        Path dataDirectory = root.resolve("data");
        Path automaticBackupsDirectory = root.resolve("backups").resolve("automatic");
        Path manualBackupsDirectory = root.resolve("backups").resolve("manual");
        Path exportsDirectory = root.resolve("exports");
        Path logsDirectory = root.resolve("logs");
        Path runtimeDirectory = root.resolve("runtime");

        try {
            Files.createDirectories(dataDirectory);
            Files.createDirectories(automaticBackupsDirectory);
            Files.createDirectories(manualBackupsDirectory);
            Files.createDirectories(exportsDirectory);
            Files.createDirectories(logsDirectory);
            Files.createDirectories(runtimeDirectory);
            verifyWritable(dataDirectory);
        } catch (IOException | SecurityException exception) {
            throw new StorageInitializationException(
                    "Das Anwendungsverzeichnis '" + root + "' kann nicht verwendet werden.",
                    exception
            );
        }

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

    private static void verifyWritable(Path dataDirectory) throws IOException {
        Path probe = Files.createTempFile(dataDirectory, ".csc-x-tool-write-", ".probe");
        Files.deleteIfExists(probe);
    }
}
