package de.venomenon.cscxtool.system;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class DesktopLifecycle {

    private final DesktopLaunchCoordinator coordinator;
    private final String applicationVersion;

    DesktopLifecycle(DesktopLaunchCoordinator coordinator, ObjectProvider<BuildProperties> buildProperties) {
        this.coordinator = coordinator;
        BuildProperties properties = buildProperties.getIfAvailable();
        this.applicationVersion = properties == null ? packageBuildVersion() : properties.getVersion();
    }

    @EventListener
    void publishAfterSuccessfulStartup(ApplicationReadyEvent event) {
        if (!coordinator.enabled()) return;
        WebServerApplicationContext context = (WebServerApplicationContext) event.getApplicationContext();
        int port = context.getWebServer().getPort();
        coordinator.publishRuntimeInfo(port, applicationVersion);
        coordinator.openPublishedInstance(port);
    }

    @jakarta.annotation.PreDestroy
    void closeRuntimeLease() {
        coordinator.close();
    }

    private static String packageBuildVersion() {
        String version = DesktopLifecycle.class.getPackage().getImplementationVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("Die Build-Metadaten für den Desktop-Launcher sind nicht verfügbar.");
        }
        return version;
    }
}
