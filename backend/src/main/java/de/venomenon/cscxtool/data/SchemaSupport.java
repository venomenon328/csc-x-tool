package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;

final class SchemaSupport {

    static final int CURRENT_SCHEMA_VERSION = 14;
    static final String CHANGELOG = "classpath:/db/changelog/db.changelog-master.yaml";
    static final String CHANGELOG_RESOURCE = "db/changelog/db.changelog-master.yaml";

    private SchemaSupport() { }

    static void migrate(DataSource dataSource) throws Exception {
        migrate(dataSource, CHANGELOG);
    }

    static void migrate(DataSource dataSource, String changeLog) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.afterPropertiesSet();
    }

    static void migrate(Path databaseFile) throws Exception {
        migrate(SqliteDataSourceFactory.create(databaseFile));
    }

    static int schemaVersion(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            return 0;
        }
    }

    static void verify(Path databaseFile, int maximumSchemaVersion) {
        DataSource source = SqliteDataSourceFactory.create(databaseFile);
        try (Connection connection = source.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("PRAGMA quick_check")) {
                if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                    throw new BackupFileException("BACKUP_INTEGRITY_FAILED", "Die Sicherung besteht die SQLite-Integrit\u00e4tspr\u00fcfung nicht.");
                }
            }
            try (ResultSet result = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (result.next()) {
                    throw new BackupFileException("BACKUP_INTEGRITY_FAILED", "Die Sicherung enth\u00e4lt ung\u00fcltige Datenbeziehungen.");
                }
            }
            int version = schemaVersion(source);
            if (version > maximumSchemaVersion) {
                throw new BackupFileException("BACKUP_SCHEMA_TOO_NEW", "Die Sicherung wurde mit einer neueren Datenbankschemaversion erstellt.");
            }
        } catch (BackupFileException exception) {
            throw exception;
        } catch (SQLException | RuntimeException exception) {
            throw new BackupFileException("BACKUP_INVALID", "Die Sicherung enth\u00e4lt keine lesbare SQLite-Datenbank.", exception);
        }
    }
}
