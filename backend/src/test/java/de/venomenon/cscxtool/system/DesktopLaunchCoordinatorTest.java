package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DesktopLaunchCoordinatorTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void keepsOneOsLockedInstanceAndReopensOnlyTheValidatedLoopbackPort() throws Exception {
        Path storageRoot = temporaryDirectory.resolve("runtime");
        String[] arguments = desktopArguments(storageRoot);
        DesktopLaunchCoordinator first = DesktopLaunchCoordinator.prepare(arguments);
        assertThat(first.shouldStartServer()).isTrue();

        HttpServer healthServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        healthServer.createContext("/api/system/health", exchange -> {
            byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        healthServer.start();
        try {
            int port = healthServer.getAddress().getPort();
            first.publishRuntimeInfo(port, "0.1.0");
            String runtimeInfo = Files.readString(storageRoot.resolve("runtime/instance.json"));
            assertThat(runtimeInfo).contains("\"formatVersion\": 1", "\"port\": " + port).doesNotContain("http://");

            DesktopLaunchCoordinator second = DesktopLaunchCoordinator.prepare(arguments);
            try {
                assertThat(second.shouldStartServer()).isFalse();
            } finally {
                second.close();
            }
        } finally {
            healthServer.stop(0);
            first.close();
        }
        assertThat(storageRoot.resolve("runtime/instance.json")).doesNotExist();
    }

    @Test
    void removesStaleRuntimeInformationOnlyAfterAcquiringTheExclusiveLock() throws Exception {
        Path storageRoot = temporaryDirectory.resolve("stale-runtime");
        ApplicationStorage storage = ApplicationStorage.prepare(storageRoot);
        Files.writeString(storage.runtimeDirectory().resolve("instance.json"), "{\"port\":99999,\"url\":\"https://example.invalid\"}");

        DesktopLaunchCoordinator coordinator = DesktopLaunchCoordinator.prepare(desktopArguments(storageRoot));
        try {
            assertThat(coordinator.shouldStartServer()).isTrue();
            assertThat(storage.runtimeDirectory().resolve("instance.json")).doesNotExist();
        } finally {
            coordinator.close();
        }
    }

    @Test
    void opensTheLoopbackUrlThroughTheWindowsDefaultUrlHandlerWithoutStartingABrowserInTheTest() {
        URI expected = URI.create("http://127.0.0.1:18452/");
        AtomicReference<URI> opened = new AtomicReference<>();
        DesktopLaunchCoordinator coordinator = DesktopLaunchCoordinator.forBrowserLaunchTest(opened::set, (message, exception) -> {
            throw new AssertionError("Browseröffnung darf nicht fehlschlagen", exception);
        });

        coordinator.openPublishedInstance(18452);

        assertThat(opened).hasValue(expected);
        assertThat(DesktopLaunchCoordinator.windowsUrlHandlerCommand(expected, "C:\\Windows\\"))
                .containsExactly(
                        "C:\\Windows\\System32\\rundll32.exe",
                        "url.dll,FileProtocolHandler",
                        "http://127.0.0.1:18452/"
                );
    }

    @Test
    void offersTheLoopbackUrlWhenTheWindowsUrlHandlerCannotBeStarted() {
        AtomicReference<String> shownMessage = new AtomicReference<>();
        AtomicReference<Exception> shownException = new AtomicReference<>();
        IOException failure = new IOException("rundll32.exe konnte nicht gestartet werden");
        DesktopLaunchCoordinator coordinator = DesktopLaunchCoordinator.forBrowserLaunchTest(
                uri -> {
                    throw failure;
                },
                (message, exception) -> {
                    shownMessage.set(message);
                    shownException.set(exception);
                }
        );

        coordinator.openPublishedInstance(18453);

        assertThat(shownMessage.get())
                .contains("Windows-Standardbrowser", "http://127.0.0.1:18453/");
        assertThat(shownException).hasValue(failure);
    }

    private static String[] desktopArguments(Path storageRoot) {
        return new String[]{
                "--csc-x-tool.desktop.enabled=true",
                "--csc-x-tool.desktop.suppress-browser=true",
                "--csc-x-tool.storage.root=" + storageRoot.toAbsolutePath()
        };
    }
}
