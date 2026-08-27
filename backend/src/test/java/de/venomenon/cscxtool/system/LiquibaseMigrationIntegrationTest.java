package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Path;

class LiquibaseMigrationIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesAnEmptySqliteFileAndKeepsRenamedSeedsOnTheNextRun() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("migration.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);

        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM motto_show", Integer.class)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM motto_show WHERE show_number = 9", String.class
        )).isEqualTo("TBA");

        jdbcTemplate.update("UPDATE motto_show SET name = ? WHERE show_number = ?", "Persistiert", 9);
        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT name FROM motto_show WHERE show_number = 9", String.class
        )).isEqualTo("Persistiert");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM motto_show", Integer.class)).isEqualTo(12);
    }

    @Test
    void enforcesTheP1ShowNumberAndNameConstraints() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("constraints.db"));
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO motto_show (show_number, name, created_at, updated_at)
                VALUES (1, 'Duplicate', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO motto_show (show_number, name, created_at, updated_at)
                VALUES (13, 'Outside range', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO motto_show (show_number, name, created_at, updated_at)
                VALUES (12, '   ', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void upgradesAnExistingP1DatabaseWithoutChangingRenamedShows() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("p1-upgrade.db"));
        migrate(dataSource, "classpath:/db/changelog/p1-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("UPDATE motto_show SET name = ? WHERE show_number = ?", "P1 bleibt erhalten", 9);

        migrate(dataSource);

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM motto_show WHERE show_number = 9", String.class))
                .isEqualTo("P1 bleibt erhalten");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM candidate", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM pragma_table_info('motto_show') WHERE name = 'selected_candidate_id'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void upgradesAnExistingP2DatabaseWithoutChangingShowOrCandidateData() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("p2-upgrade.db"));
        migrate(dataSource, "classpath:/db/changelog/p2-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("UPDATE motto_show SET name = ? WHERE show_number = ?", "P2 bleibt erhalten", 9);
        jdbcTemplate.update("""
                INSERT INTO candidate (motto_show_id, artist, title, youtube_url, status, manual_position, created_at, updated_at)
                VALUES (4, 'Bestehender Interpret', 'Bestehender Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'FINALIST', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long candidateId = jdbcTemplate.queryForObject("SELECT id FROM candidate WHERE motto_show_id = 4", Long.class);
        jdbcTemplate.update("UPDATE motto_show SET selected_candidate_id = ? WHERE id = 4", candidateId);

        migrate(SqliteDataSourceFactory.create(temporaryDirectory.resolve("p2-upgrade.db")));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM motto_show WHERE show_number = 9", String.class))
                .isEqualTo("P2 bleibt erhalten");
        assertThat(jdbcTemplate.queryForObject("SELECT artist FROM candidate WHERE id = ?", String.class, candidateId))
                .isEqualTo("Bestehender Interpret");
        assertThat(jdbcTemplate.queryForObject("SELECT selected_candidate_id FROM motto_show WHERE id = 4", Long.class))
                .isEqualTo(candidateId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM participant", Integer.class)).isZero();
    }

    @Test
    void enforcesTheP3ParticipantAndAliasConstraints() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("participant-constraints.db"));
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES (' ', 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Test', 'de', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Test', 'DE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO participant_alias (participant_id, alias) VALUES (999, 'Alias')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void upgradesAnExistingP3DatabaseWithoutChangingItsExistingData() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("p3-upgrade.db"));
        migrate(dataSource, "classpath:/db/changelog/p3-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("UPDATE motto_show SET name = ? WHERE show_number = ?", "P3 bleibt erhalten", 9);
        jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Bestehender Teilnehmer', 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long participantId = jdbcTemplate.queryForObject("SELECT id FROM participant", Long.class);
        jdbcTemplate.update("INSERT INTO participant_alias (participant_id, alias) VALUES (?, ?)", participantId, "Alias");

        migrate(SqliteDataSourceFactory.create(temporaryDirectory.resolve("p3-upgrade.db")));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM motto_show WHERE show_number = 9", String.class))
                .isEqualTo("P3 bleibt erhalten");
        assertThat(jdbcTemplate.queryForObject("SELECT display_name FROM participant WHERE id = ?", String.class, participantId))
                .isEqualTo("Bestehender Teilnehmer");
        assertThat(jdbcTemplate.queryForObject("SELECT alias FROM participant_alias WHERE participant_id = ?", String.class, participantId))
                .isEqualTo("Alias");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pragma_table_info('contest_entry')", Integer.class)).isEqualTo(12);
    }

    @Test
    void enforcesTheP4ContestEntryConstraints() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("entry-constraints.db"));
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, created_at, updated_at)
                VALUES (1, ' ', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 2, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 0, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret 2', 'Titel 2', 'https://www.youtube.com/watch?v=9bZkp7q19f0', 0, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
    }

    private void migrate(DataSource dataSource) throws Exception {
        migrate(dataSource, "classpath:/db/changelog/db.changelog-master.yaml");
    }

    private void migrate(DataSource dataSource, String changeLog) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.afterPropertiesSet();
    }
}
