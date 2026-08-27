package de.venomenon.cscxtool;

import liquibase.exception.LiquibaseException;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CscXToolApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(CscXToolApplication.class, args);
        } catch (RuntimeException exception) {
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
}
