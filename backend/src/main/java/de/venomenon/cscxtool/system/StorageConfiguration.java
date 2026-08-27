package de.venomenon.cscxtool.system;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class StorageConfiguration {

    @Bean
    ApplicationStorage applicationStorage(StorageProperties properties) {
        return ApplicationStorage.prepare(properties.getRoot());
    }

    @Bean
    DataSource dataSource(ApplicationStorage storage) {
        return SqliteDataSourceFactory.create(storage.databaseFile());
    }
}
