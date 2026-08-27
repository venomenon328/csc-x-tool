package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void keepsTheOriginalCauseAndNamesThePathWhenStorageRootIsNotUsable() throws Exception {
        Path fileInsteadOfDirectory = Files.createFile(temporaryDirectory.resolve("not-a-directory"));

        assertThatThrownBy(() -> ApplicationStorage.prepare(fileInsteadOfDirectory))
                .isInstanceOf(StorageInitializationException.class)
                .hasMessageContaining(fileInsteadOfDirectory.toAbsolutePath().toString())
                .hasCauseInstanceOf(Exception.class);
    }
}
