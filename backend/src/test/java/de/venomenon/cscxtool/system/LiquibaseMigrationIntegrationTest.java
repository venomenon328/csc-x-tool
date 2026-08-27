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
