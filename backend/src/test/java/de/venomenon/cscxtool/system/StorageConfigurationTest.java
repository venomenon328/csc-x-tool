package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class StorageConfigurationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void supportsTheDocumentedStorageRootEnvironmentVariableAlias() {
        Path environmentRoot = temporaryDirectory.resolve("environment-root");
        StorageProperties properties = new StorageProperties();
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CSC_X_TOOL_STORAGE_ROOT", environmentRoot.toString());

        ApplicationStorage storage = new StorageConfiguration().applicationStorage(properties, environment);

        assertThat(storage.root()).isEqualTo(environmentRoot.toAbsolutePath().normalize());
        assertThat(storage.databaseFile()).isEqualTo(
                environmentRoot.resolve("data/csc-x-tool.db").toAbsolutePath().normalize()
        );
    }

    @Test
    void givesTheCanonicalStoragePropertyPrecedenceOverTheEnvironmentAlias() {
        Path canonicalRoot = temporaryDirectory.resolve("canonical-root");
        StorageProperties properties = new StorageProperties();
        properties.setRoot(canonicalRoot);
        MockEnvironment environment = new MockEnvironment()
                .withProperty("CSC_X_TOOL_STORAGE_ROOT", temporaryDirectory.resolve("environment-root").toString());

        ApplicationStorage storage = new StorageConfiguration().applicationStorage(properties, environment);

        assertThat(storage.root()).isEqualTo(canonicalRoot.toAbsolutePath().normalize());
    }
}
