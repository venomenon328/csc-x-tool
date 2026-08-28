package de.venomenon.cscxtool.system;

import de.venomenon.cscxtool.data.DatabaseAccessLock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Service;

@Service
class ShutdownLifecycleService {

    private final AtomicBoolean shutdownRequested = new AtomicBoolean();
    private final DatabaseAccessLock databaseAccessLock;
    private final ConfigurableApplicationContext applicationContext;

    ShutdownLifecycleService(DatabaseAccessLock databaseAccessLock, ConfigurableApplicationContext applicationContext) {
        this.databaseAccessLock = databaseAccessLock;
        this.applicationContext = applicationContext;
    }

    boolean requestShutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) return false;
        Thread.ofPlatform().name("csc-x-tool-controlled-shutdown").start(() -> {
            try {
                Thread.sleep(Duration.ofMillis(150));
                databaseAccessLock.withExclusive(() -> {
                    applicationContext.close();
                    return null;
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        return true;
    }

    boolean isShutdownRequested() {
        return shutdownRequested.get();
    }
}
