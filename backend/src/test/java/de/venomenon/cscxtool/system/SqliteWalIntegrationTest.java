package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteWalIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void enablesWalAndConnectionLocalPragmasForEachRealFileConnection() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("wal-spike.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);

        try (Connection first = dataSource.getConnection(); Connection second = dataSource.getConnection()) {
            assertThat(pragmaValue(first, "foreign_keys")).isEqualTo("1");
            assertThat(pragmaValue(second, "foreign_keys")).isEqualTo("1");
            assertThat(pragmaValue(first, "busy_timeout")).isEqualTo("5000");
            assertThat(pragmaValue(second, "busy_timeout")).isEqualTo("5000");

            try (Statement statement = first.createStatement()) {
                statement.execute("CREATE TABLE parent (id INTEGER PRIMARY KEY)");
                statement.execute("CREATE TABLE child (parent_id INTEGER REFERENCES parent(id))");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = second.createStatement()) {
                    statement.executeUpdate("INSERT INTO child (parent_id) VALUES (999)");
                }
            }).hasMessageContaining("FOREIGN KEY constraint failed");
        }

        try (Connection reopened = SqliteDataSourceFactory.create(databaseFile).getConnection()) {
            assertThat(pragmaValue(reopened, "journal_mode")).isEqualTo("wal");
        }
    }

    @Test
    void permitsAWriterWhileAnotherConnectionKeepsAReadSnapshot() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("reader-writer.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);

        try (Connection setup = dataSource.getConnection(); Statement statement = setup.createStatement()) {
            statement.execute("CREATE TABLE wal_probe (value TEXT)");
            statement.execute("INSERT INTO wal_probe (value) VALUES ('before')");
        }

        try (Connection reader = dataSource.getConnection(); Connection writer = dataSource.getConnection()) {
            reader.setAutoCommit(false);
            assertThat(rowCount(reader)).isEqualTo(1);

            try (Statement statement = writer.createStatement()) {
                assertThat(statement.executeUpdate("INSERT INTO wal_probe (value) VALUES ('after')")).isEqualTo(1);
            }

            assertThat(rowCount(reader)).isEqualTo(1);
            reader.commit();
            assertThat(rowCount(reader)).isEqualTo(2);
        }
    }

    private String pragmaValue(Connection connection, String pragma) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("PRAGMA " + pragma)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }

    private int rowCount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM wal_probe")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }
}
