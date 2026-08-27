package de.venomenon.cscxtool.system;

import java.nio.file.Path;
import javax.sql.DataSource;
import de.venomenon.cscxtool.data.DatabaseAccessLock;
import de.venomenon.cscxtool.data.LockedDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
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
    DatabaseStartupState databaseStartupState(ApplicationStorage storage) {
        return new DatabaseStartupState(java.nio.file.Files.exists(storage.databaseFile()));
    }

    @Bean(name = "sqliteDataSource")
    DataSource sqliteDataSource(ApplicationStorage storage) {
        return SqliteDataSourceFactory.create(storage.databaseFile());
    }

    @Bean
    @Primary
    DataSource dataSource(@Qualifier("sqliteDataSource") DataSource sqliteDataSource, DatabaseAccessLock accessLock) {
        return new LockedDataSource(sqliteDataSource, accessLock);
    }
}
