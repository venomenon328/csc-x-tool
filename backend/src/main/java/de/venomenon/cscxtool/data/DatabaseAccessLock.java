package de.venomenon.cscxtool.data;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The process-wide database gate. Borrowing a regular JDBC connection holds a read lock; a
 * restore obtains the write lock, therefore no repository operation can overlap the final switch.
 */
@Component
public class DatabaseAccessLock {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);

    public void acquireRead() {
        lock.readLock().lock();
    }

    public void releaseRead() {
        lock.readLock().unlock();
    }

    public <T> T withExclusive(Supplier<T> operation) {
        lock.writeLock().lock();
        try {
            return operation.get();
        } finally {
            lock.writeLock().unlock();
        }
    }
}
