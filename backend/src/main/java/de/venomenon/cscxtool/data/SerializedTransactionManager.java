package de.venomenon.cscxtool.data;

import java.util.concurrent.locks.ReentrantLock;
import javax.sql.DataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/** Serializes application transactions before borrowing a connection or establishing a SQLite snapshot.
 * The separate restore gate is still held by LockedDataSource; no read-to-write upgrade is involved.
 */
public final class SerializedTransactionManager extends JdbcTransactionManager {
    private final ReentrantLock transactions = new ReentrantLock(true);

    public SerializedTransactionManager(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        transactions.lock();
        try {
            super.doBegin(transaction, definition);
        } catch (RuntimeException | Error failure) {
            transactions.unlock();
            throw failure;
        }
    }

    @Override
    protected void doCleanupAfterCompletion(Object transaction) {
        try {
            super.doCleanupAfterCompletion(transaction);
        } finally {
            transactions.unlock();
        }
    }
}
