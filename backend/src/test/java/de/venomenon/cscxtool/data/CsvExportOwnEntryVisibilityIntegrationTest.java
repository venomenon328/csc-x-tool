package de.venomenon.cscxtool.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
class CsvExportOwnEntryVisibilityIntegrationTest {

    private static final Path STORAGE_ROOT = temporaryStorageRoot();

    @Autowired private JdbcTemplate jdbc;
    @Autowired private CsvExportService csv;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("csc-x-tool.storage.root", () -> STORAGE_ROOT.toString());
    }

    @Test
    void keepsHistoricalSubmitterAndCountryVisibleWithoutAClosedCurrentBallot() {
        jdbc.update("""
                INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at)
                VALUES (9100,'CSV Archiv',9100,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO participant (id,display_name,active,created_at,updated_at)
                VALUES (9100,'Archivnutzer CSV',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at)
                VALUES (9100,9100,9100,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO motto_show (id,contest_id,show_number,name,entry_list_complete,created_at,updated_at)
                VALUES (9100,9100,1,'Historische CSV-Show',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);
        jdbc.update("""
                INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,
                                           contest_participation_id,created_at,updated_at)
                VALUES (9100,9100,9100,'Archivband','Archivsong',NULL,1,9100,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """);

        String exported = new String(csv.contestEntries(), StandardCharsets.UTF_8);

        assertThat(exported).contains("CSV Archiv", "Archivband", "Archivsong", "Archivnutzer CSV", ";DE");
    }

    private static Path temporaryStorageRoot() {
        try { return Files.createTempDirectory("csc-x-tool-csv-own-entry-"); }
        catch (Exception exception) { throw new IllegalStateException("Temporäres SQLite-Testverzeichnis konnte nicht erstellt werden.", exception); }
    }
}
