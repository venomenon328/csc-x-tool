package de.venomenon.cscxtool.system;

import java.io.PrintWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.logging.Logger;
import javax.sql.DataSource;
import org.sqlite.SQLiteDataSource;

/**
 * Creates SQLite data sources with the application's connection contract.
 *
 * <p>Foreign-key checks and the busy timeout are connection-local SQLite settings, therefore this
 * wrapper deliberately applies them every time a connection is borrowed. WAL is a database-file
 * setting and is activated once while the data source is created.</p>
 */
public final class SqliteDataSourceFactory {

    private static final int BUSY_TIMEOUT_MILLIS = 5_000;

    private SqliteDataSourceFactory() {
    }

    public static DataSource create(Path databaseFile) {
        SQLiteDataSource delegate = new SQLiteDataSource();
        delegate.setUrl("jdbc:sqlite:" + databaseFile.toAbsolutePath().normalize());
        enableWal(delegate, databaseFile);
        return new ConnectionConfiguringDataSource(delegate);
    }

    private static void enableWal(SQLiteDataSource dataSource, Path databaseFile) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
        } catch (SQLException exception) {
            throw new StorageInitializationException(
                    "Der WAL-Modus für die SQLite-Datenbank '" + databaseFile + "' konnte nicht aktiviert werden.",
                    exception
            );
        }
    }

    private static final class ConnectionConfiguringDataSource implements DataSource {

        private final DataSource delegate;

        private ConnectionConfiguringDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return configure(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return configure(delegate.getConnection(username, password));
        }

        private Connection configure(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=" + BUSY_TIMEOUT_MILLIS);
                return connection;
            } catch (SQLException exception) {
                try {
                    connection.close();
                } catch (SQLException closeException) {
                    exception.addSuppressed(closeException);
                }
                throw exception;
            }
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return delegate.getLogWriter();
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {
            delegate.setLogWriter(out);
        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {
            delegate.setLoginTimeout(seconds);
        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return delegate.getLoginTimeout();
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return delegate.getParentLogger();
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            if (iface.isInstance(this)) {
                return iface.cast(this);
            }
            return delegate.unwrap(iface);
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return iface.isInstance(this) || delegate.isWrapperFor(iface);
        }
    }
}
