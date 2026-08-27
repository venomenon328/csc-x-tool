package de.venomenon.cscxtool.data;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Uses Xerial's BACKUP/RESTORE extension, which delegates to SQLite's online backup API. Keeping
 * this small adapter separate prevents accidental file copying of a live WAL database.
 */
@Component
public class SqliteOnlineBackupAdapter {

    public void backup(DataSource source, Path target) throws SQLException {
        execute(source, "backup to " + quote(target));
    }

    public void restore(DataSource target, Path source) throws SQLException {
        execute(target, "restore from " + quote(source));
    }

    private static void execute(DataSource dataSource, String command) throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute(command);
        }
    }

    private static String quote(Path path) {
        return "'" + path.toAbsolutePath().normalize().toString().replace("'", "''") + "'";
    }
}
