package de.venomenon.cscxtool.data;

import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** Adds the read side of {@link DatabaseAccessLock} to every borrowed JDBC connection. */
public final class LockedDataSource implements DataSource {

    private final DataSource delegate;
    private final DatabaseAccessLock accessLock;

    public LockedDataSource(DataSource delegate, DatabaseAccessLock accessLock) {
        this.delegate = delegate;
        this.accessLock = accessLock;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return locked(delegate::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return locked(() -> delegate.getConnection(username, password));
    }

    private Connection locked(ConnectionSupplier supplier) throws SQLException {
        accessLock.acquireRead();
        try {
            Connection connection = supplier.get();
            AtomicBoolean released = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close") && method.getParameterCount() == 0) {
                            try {
                                return method.invoke(connection);
                            } catch (InvocationTargetException exception) {
                                throw exception.getCause();
                            } finally {
                                if (released.compareAndSet(false, true)) {
                                    accessLock.releaseRead();
                                }
                            }
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    }
            );
        } catch (SQLException | RuntimeException exception) {
            accessLock.releaseRead();
            throw exception;
        }
    }

    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return iface.isInstance(this) || delegate.isWrapperFor(iface); }

    @FunctionalInterface
    private interface ConnectionSupplier { Connection get() throws SQLException; }
}
