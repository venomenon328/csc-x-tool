package de.venomenon.cscxtool.show;

import static org.assertj.core.api.Assertions.assertThat;

import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MottoShowRepositoryIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Autowired
    private MottoShowRepository repository;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void readsAllSeededShowsInNumberOrderAndPersistsARenameAcrossAReopenedDataSource() {
        var shows = repository.findAll(1);
        assertThat(shows)
                .hasSize(12)
                .extracting(MottoShow::showNumber)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        MottoShow ninthShow = shows.stream()
                .filter(show -> show.showNumber() == 9)
                .findFirst()
                .orElseThrow();
        assertThat(repository.rename(ninthShow.id(), "Neue neunte Show")).isTrue();

        DataSource reopenedDataSource = SqliteDataSourceFactory.create(STORAGE_ROOT.resolve("data/csc-x-tool.db"));
        JdbcTemplate reopenedJdbcTemplate = new JdbcTemplate(reopenedDataSource);
        assertThat(reopenedJdbcTemplate.queryForObject(
                "SELECT name FROM motto_show WHERE show_number = 9", String.class
        )).isEqualTo("Neue neunte Show");
    }

    private static Path temporaryStorageRoot() {
        try {
            return Files.createTempDirectory("csc-x-tool-repository-");
        } catch (Exception exception) {
            throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht angelegt werden.", exception);
        }
    }
}
