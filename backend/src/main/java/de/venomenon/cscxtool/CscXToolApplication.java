package de.venomenon.cscxtool;

import liquibase.exception.LiquibaseException;
import java.awt.GraphicsEnvironment;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import de.venomenon.cscxtool.system.DesktopLaunchCoordinator;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class CscXToolApplication {

    public static void main(String[] args) {
        DesktopLaunchCoordinator desktop;
        try {
            desktop = DesktopLaunchCoordinator.prepare(args);
        } catch (RuntimeException exception) {
            showDesktopStartupFailure(exception);
            throw exception;
        }
        if (!desktop.shouldStartServer()) {
            return;
        }
        try {
            SpringApplication application = new SpringApplication(CscXToolApplication.class);
            application.addInitializers(context -> context.getBeanFactory()
                    .registerSingleton("desktopLaunchCoordinator", desktop));
            application.run(args);
        } catch (RuntimeException exception) {
            desktop.close();
            showDesktopStartupFailure(exception);
            if (hasCause(exception, LiquibaseException.class)) {
                System.err.println(
                        "CSC X Tool konnte nicht starten, weil die Datenbankmigration fehlgeschlagen ist. "
                                + "Die technische Ursache steht direkt darunter."
                );
            }
            throw exception;
        }
    }

    private static boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable current = exception;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void showDesktopStartupFailure(RuntimeException exception) {
        if (!Boolean.getBoolean(DesktopLaunchCoordinator.DESKTOP_ENABLED) || GraphicsEnvironment.isHeadless()) return;
        javax.swing.JOptionPane.showMessageDialog(
                null,
                "CSC X Tool konnte nicht gestartet werden. Prüfen Sie das lokale Anwendungsverzeichnis und die Logdatei.",
                "CSC X Tool", javax.swing.JOptionPane.ERROR_MESSAGE
        );
    }
}
