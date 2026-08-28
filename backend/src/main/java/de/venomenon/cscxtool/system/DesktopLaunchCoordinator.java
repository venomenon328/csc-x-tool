package de.venomenon.cscxtool.system;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the desktop-mode OS lock before Spring starts. Keeping this out of the Spring context
 * prevents two concurrent launcher calls from both reaching migration and P7 startup backup work.
 */
public final class DesktopLaunchCoordinator implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopLaunchCoordinator.class);
    public static final String DESKTOP_ENABLED = "csc-x-tool.desktop.enabled";
    private static final String STORAGE_ROOT = "csc-x-tool.storage.root";
    private static final String INSTANCE_FILE = "instance.json";
    private static final String LOCK_FILE = "instance.lock";
    private static final Duration HEALTH_CONNECT_TIMEOUT = Duration.ofMillis(750);
    private static final Duration HEALTH_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration INSTANCE_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration INSTANCE_START_POLL = Duration.ofMillis(200);
    private static final Pattern PORT = Pattern.compile("\\\"port\\\"\\s*:\\s*(\\d+)");

    private final boolean enabled;
    private final boolean suppressBrowser;
    private final ApplicationStorage storage;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private boolean closed;

    private DesktopLaunchCoordinator(
            boolean enabled, boolean suppressBrowser, ApplicationStorage storage, FileChannel lockChannel, FileLock lock
    ) {
        this.enabled = enabled;
        this.suppressBrowser = suppressBrowser;
        this.storage = storage;
        this.lockChannel = lockChannel;
        this.lock = lock;
    }

    public static DesktopLaunchCoordinator prepare(String[] arguments) {
        if (!isEnabled(arguments)) {
            return disabled();
        }

        System.setProperty(DESKTOP_ENABLED, "true");
        Path configuredRoot = configuredStorageRoot(arguments).map(Path::of).orElse(null);
        ApplicationStorage storage = ApplicationStorage.prepare(configuredRoot);
        System.setProperty("logging.file.name", storage.logsDirectory().resolve("csc-x-tool.log").toString());
        String browserSuppression = option(arguments, "csc-x-tool.desktop.suppress-browser")
                .orElseGet(() -> System.getProperty(
                        "csc-x-tool.desktop.suppress-browser", System.getenv("CSC_X_TOOL_DESKTOP_SUPPRESS_BROWSER")
                ));
        boolean suppressBrowser = isTrue(browserSuppression);

        Integer knownPort = healthyPort(storage);
        if (knownPort != null) {
            if (!suppressBrowser) openExistingOrReport(knownPort, storage);
            return new DesktopLaunchCoordinator(true, suppressBrowser, storage, null, null);
        }

        Path lockFile = storage.runtimeDirectory().resolve(LOCK_FILE);
        try {
            FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock lock = tryAcquire(channel);
            if (lock != null) {
                Integer racePort = healthyPort(storage);
                if (racePort != null) {
                    release(channel, lock);
                    if (!suppressBrowser) openExistingOrReport(racePort, storage);
                    return new DesktopLaunchCoordinator(true, suppressBrowser, storage, null, null);
                }
                deleteStaleInstance(storage);
                return new DesktopLaunchCoordinator(true, suppressBrowser, storage, channel, lock);
            }
            channel.close();
        } catch (IOException exception) {
            showError("CSC X Tool konnte die lokale Instanzsperre nicht anlegen.", exception);
            return new DesktopLaunchCoordinator(true, suppressBrowser, storage, null, null);
        }

        Instant deadline = Instant.now().plus(INSTANCE_START_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Integer port = healthyPort(storage);
            if (port != null) {
                if (!suppressBrowser) openExistingOrReport(port, storage);
                return new DesktopLaunchCoordinator(true, suppressBrowser, storage, null, null);
            }
            try {
                Thread.sleep(INSTANCE_START_POLL);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        showError(
                "CSC X Tool startet bereits, wurde aber nicht rechtzeitig bereit. Bitte warten Sie einen Moment und versuchen Sie es erneut.",
                null
        );
        return new DesktopLaunchCoordinator(true, suppressBrowser, storage, null, null);
    }

    public boolean shouldStartServer() {
        return !enabled || lock != null;
    }

    public static DesktopLaunchCoordinator disabled() {
        return new DesktopLaunchCoordinator(false, false, null, null, null);
    }

    public boolean enabled() {
        return enabled;
    }

    public ApplicationStorage storage() {
        return storage;
    }

    public void publishRuntimeInfo(int port, String applicationVersion) {
        if (!enabled || lock == null) return;
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Der Desktop-Launcher hat keinen gültigen Loopback-Port erhalten.");
        }
        Path target = storage.runtimeDirectory().resolve(INSTANCE_FILE);
        Path temporary = storage.runtimeDirectory().resolve("." + INSTANCE_FILE + "-" + UUID.randomUUID() + ".tmp");
        String content = "{\n"
                + "  \"formatVersion\": 1,\n"
                + "  \"pid\": " + ProcessHandle.current().pid() + ",\n"
                + "  \"port\": " + port + ",\n"
                + "  \"startedAt\": \"" + Instant.now() + "\",\n"
                + "  \"applicationVersion\": \"" + escape(applicationVersion) + "\"\n"
                + "}\n";
        try {
            Files.writeString(temporary, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The next launcher run removes stale temporary runtime artifacts.
            }
            throw new IllegalStateException("Die Laufzeitinformationen konnten nicht atomar gespeichert werden.", exception);
        }
    }

    public void openPublishedInstance(int port) {
        if (!enabled || suppressBrowser) return;
        openExistingOrReport(port, storage);
    }

    @Override
    public void close() {
        if (closed || !enabled) return;
        closed = true;
        if (lock != null) {
            deleteStaleInstance(storage);
            release(lockChannel, lock);
        }
    }

    private static boolean isEnabled(String[] arguments) {
        return isTrue(option(arguments, DESKTOP_ENABLED).orElseGet(
                () -> System.getProperty(DESKTOP_ENABLED, System.getenv("CSC_X_TOOL_DESKTOP_ENABLED"))
        ));
    }

    private static Optional<String> configuredStorageRoot(String[] arguments) {
        return option(arguments, STORAGE_ROOT)
                .or(() -> Optional.ofNullable(System.getProperty(STORAGE_ROOT)))
                .or(() -> Optional.ofNullable(System.getenv("CSC_X_TOOL_STORAGE_ROOT")).filter(value -> !value.isBlank()));
    }

    private static Optional<String> option(String[] arguments, String name) {
        String prefix = "--" + name + "=";
        for (String argument : arguments) {
            if (argument.startsWith(prefix)) return Optional.of(argument.substring(prefix.length()));
        }
        return Optional.empty();
    }

    private static boolean isTrue(String value) {
        return value != null && Boolean.parseBoolean(value);
    }

    private static FileLock tryAcquire(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        }
    }

    private static Integer healthyPort(ApplicationStorage storage) {
        Path instance = storage.runtimeDirectory().resolve(INSTANCE_FILE);
        if (!Files.isRegularFile(instance)) return null;
        try {
            String json = Files.readString(instance);
            Matcher matcher = PORT.matcher(json);
            if (!matcher.find()) return null;
            int port = Integer.parseInt(matcher.group(1));
            if (port < 1 || port > 65535 || !isHealthy(port)) return null;
            return port;
        } catch (IOException | NumberFormatException exception) {
            return null;
        }
    }

    private static boolean isHealthy(int port) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(HEALTH_CONNECT_TIMEOUT)
                    .build();
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(urlFor(port) + "api/system/health"))
                            .timeout(HEALTH_REQUEST_TIMEOUT)
                            .GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            return response.statusCode() == 200 && response.body().contains("\"status\":\"UP\"");
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void deleteStaleInstance(ApplicationStorage storage) {
        try {
            Files.deleteIfExists(storage.runtimeDirectory().resolve(INSTANCE_FILE));
        } catch (IOException ignored) {
            // A later regular shutdown or launcher start reports a genuine storage issue if needed.
        }
    }

    private static void release(FileChannel channel, FileLock lock) {
        try {
            lock.release();
        } catch (IOException ignored) {
            // The operating system releases the lock when the process exits.
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // The operating system releases the handle when the process exits.
        }
    }

    private static void openExistingOrReport(int port, ApplicationStorage storage) {
        URI uri = URI.create(urlFor(port));
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Der Windows-Standardbrowser ist nicht verfügbar.");
            }
            Desktop.getDesktop().browse(uri);
        } catch (Exception exception) {
            showError("CSC X Tool läuft bereits. Öffnen Sie diese lokale Adresse manuell: " + uri, exception);
        }
    }

    private static String urlFor(int port) {
        return "http://127.0.0.1:" + port + "/";
    }

    private static void showError(String message, Exception exception) {
        if (exception != null) {
            LOG.error(message, exception);
        }
        if (!GraphicsEnvironment.isHeadless()) {
            javax.swing.JOptionPane.showMessageDialog(null, message, "CSC X Tool", javax.swing.JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
