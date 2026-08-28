package de.venomenon.cscxtool.system;

import de.venomenon.cscxtool.data.BackupReason;
import de.venomenon.cscxtool.data.BackupService;
import de.venomenon.cscxtool.data.SchemaMigrationProbe;
import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Coordinates the mandatory backup boundary around the normal Liquibase lifecycle. */
@Component
public class LiquibaseStartupCoordinator {

    private final DataSource dataSource;
    private final DatabaseStartupState startupState;
    private final BackupService backups;
    private final SchemaMigrationProbe migrationProbe;
    private final String changeLog;

    @Autowired
    public LiquibaseStartupCoordinator(
            DataSource dataSource,
            DatabaseStartupState startupState,
            BackupService backups,
            SchemaMigrationProbe migrationProbe,
            Environment environment
    ) {
        this(dataSource, startupState, backups, migrationProbe,
                environment.getProperty("spring.liquibase.change-log", "classpath:/db/changelog/db.changelog-master.yaml"));
    }

    public LiquibaseStartupCoordinator(
            DataSource dataSource,
            DatabaseStartupState startupState,
            BackupService backups,
            SchemaMigrationProbe migrationProbe
    ) {
        this(dataSource, startupState, backups, migrationProbe, "classpath:/db/changelog/db.changelog-master.yaml");
    }

    private LiquibaseStartupCoordinator(
            DataSource dataSource,
            DatabaseStartupState startupState,
            BackupService backups,
            SchemaMigrationProbe migrationProbe,
            String changeLog
    ) {
        this.dataSource = dataSource;
        this.startupState = startupState;
        this.backups = backups;
        this.migrationProbe = migrationProbe;
        this.changeLog = changeLog;
    }

    @PostConstruct
    public void migrateAndBackUp() {
        try {
            if (startupState.containsExistingSchema() && migrationProbe.hasPendingChanges(dataSource, changeLog)) {
                backups.create(BackupReason.PRE_MIGRATION);
            }
            migrationProbe.migrate(dataSource, changeLog);
            backups.create(BackupReason.STARTUP);
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Die Datenbankmigration konnte nicht abgeschlossen werden.", exception);
        }
    }
}
