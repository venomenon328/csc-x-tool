package de.venomenon.cscxtool.system;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    private static final String DOCUMENTED_STORAGE_ROOT_ENVIRONMENT_VARIABLE = "CSC_X_TOOL_STORAGE_ROOT";

    @Bean
    ApplicationStorage applicationStorage(StorageProperties properties, Environment environment) {
        Path configuredRoot = properties.getRoot();
        if (configuredRoot == null) {
            String environmentRoot = environment.getProperty(DOCUMENTED_STORAGE_ROOT_ENVIRONMENT_VARIABLE);
            if (environmentRoot != null && !environmentRoot.isBlank()) {
                configuredRoot = Path.of(environmentRoot);
            }
        }
        return ApplicationStorage.prepare(configuredRoot);
    }

    @Bean
    DataSource dataSource(ApplicationStorage storage) {
        return SqliteDataSourceFactory.create(storage.databaseFile());
    }
}
