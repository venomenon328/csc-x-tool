package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.venomenon.cscxtool.CscXToolApplication;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.sqlite.SQLiteException;

class LiquibaseFailurePropagationIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void preservesTheSqliteCauseWhenALiquibaseChangesetFails() {
        SpringApplication application = new SpringApplication(CscXToolApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);

        assertThatThrownBy(() -> application.run(
                "--csc-x-tool.storage.root=" + temporaryDirectory.resolve("storage"),
                "--spring.liquibase.change-log=classpath:/db/changelog/invalid-for-failure-test.yaml"
        ))
                .hasRootCauseInstanceOf(SQLiteException.class)
                .hasStackTraceContaining("this statement is deliberately invalid");
    }
}
