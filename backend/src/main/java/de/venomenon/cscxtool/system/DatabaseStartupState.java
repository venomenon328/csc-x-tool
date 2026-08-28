package de.venomenon.cscxtool.system;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** State observed before the application's regular data source is allowed to create the database. */
public record DatabaseStartupState(boolean containsExistingSchema) {

    public static DatabaseStartupState inspect(Path databaseFile) {
        if (!Files.exists(databaseFile)) {
            return new DatabaseStartupState(false);
        }
        if (!Files.isRegularFile(databaseFile)) {
            throw new StorageInitializationException("Der vorhandene Datenbankpfad ist keine reguläre Datei.", null);
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath().normalize());
             Statement statement = connection.createStatement()) {
            try (ResultSet table = statement.executeQuery("""
                    SELECT 1 FROM sqlite_master
                    WHERE type = 'table' AND name = 'databasechangelog'
                    """)) {
                if (!table.next()) {
                    return new DatabaseStartupState(false);
                }
            }
            try (ResultSet changeSet = statement.executeQuery("SELECT 1 FROM databasechangelog LIMIT 1")) {
                return new DatabaseStartupState(changeSet.next());
            }
        } catch (SQLException exception) {
            throw new StorageInitializationException(
                    "Der vorhandene Datenbankbestand konnte vor der Migration nicht gepr\u00fcft werden.", exception
            );
        }
    }
}
