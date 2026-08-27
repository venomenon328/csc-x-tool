package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ApplicationStorageTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preparesTheCompleteConfiguredStorageLayout() {
        Path storageRoot = temporaryDirectory.resolve("csc-x-tool");

        ApplicationStorage storage = ApplicationStorage.prepare(storageRoot);

        assertThat(storage.databaseFile()).isEqualTo(storageRoot.resolve("data/csc-x-tool.db").toAbsolutePath());
        assertThat(storage.automaticBackupsDirectory()).isDirectory();
        assertThat(storage.manualBackupsDirectory()).isDirectory();
        assertThat(storage.exportsDirectory()).isDirectory();
        assertThat(storage.logsDirectory()).isDirectory();
        assertThat(storage.runtimeDirectory()).isDirectory();
    }

    @Test
    void probesEveryPreparedStorageDirectory() {
        Path storageRoot = temporaryDirectory.resolve("csc-x-tool");
        List<Path> probedDirectories = new ArrayList<>();

        ApplicationStorage storage = ApplicationStorage.prepare(storageRoot, probedDirectories::add);

        assertThat(probedDirectories).containsExactly(
                storage.dataDirectory(),
                storage.automaticBackupsDirectory(),
                storage.manualBackupsDirectory(),
                storage.exportsDirectory(),
                storage.logsDirectory(),
                storage.runtimeDirectory()
        );
    }

    @Test
    void keepsTheOriginalCauseAndNamesThePathWhenStorageRootIsNotUsable() throws Exception {
        Path fileInsteadOfDirectory = Files.createFile(temporaryDirectory.resolve("not-a-directory"));

        assertThatThrownBy(() -> ApplicationStorage.prepare(fileInsteadOfDirectory))
                .isInstanceOf(StorageInitializationException.class)
                .hasMessageContaining(fileInsteadOfDirectory.toAbsolutePath().toString())
                .hasCauseInstanceOf(Exception.class);
    }

    @ParameterizedTest
    @MethodSource("preparedDirectoryRelativePaths")
    void keepsTheOriginalCauseAndNamesTheRejectedPreparedDirectory(Path relativeDirectory) {
        Path storageRoot = temporaryDirectory.resolve("csc-x-tool");
        Path rejectedDirectory = storageRoot.resolve(relativeDirectory).toAbsolutePath().normalize();
        IOException writeFailure = new IOException("simulated write-probe failure");

        assertThatThrownBy(() -> ApplicationStorage.prepare(storageRoot, directory -> {
            if (directory.equals(rejectedDirectory)) {
                throw writeFailure;
            }
        }))
                .isInstanceOf(StorageInitializationException.class)
                .hasMessageContaining(rejectedDirectory.toString())
                .satisfies(exception -> assertThat(exception.getCause()).isSameAs(writeFailure));
    }

    private static Stream<Path> preparedDirectoryRelativePaths() {
        return Stream.of(
                Path.of("data"),
                Path.of("backups", "automatic"),
                Path.of("backups", "manual"),
                Path.of("exports"),
                Path.of("logs"),
                Path.of("runtime")
        );
    }
}
