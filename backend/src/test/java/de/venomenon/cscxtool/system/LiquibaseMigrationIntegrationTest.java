package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

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
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pragma_table_info('contest_entry')", Integer.class)).isEqualTo(13);
    }

    @Test
    void enforcesTheP4ContestEntryConstraints() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("entry-constraints.db"));
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, created_at, updated_at)
                VALUES (1, ' ', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 2, 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', NULL, NULL, 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, ranking_position, created_at, updated_at)
                VALUES (1, 'Interpret 2', 'Titel 2', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, 2, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void upgradesAnExistingP4DatabaseAndEnforcesBallotSnapshotConstraints() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("p4-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p4-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, ranking_position, created_at, updated_at)
                VALUES (1, 'Bestehender Beitrag', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 0, 0, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long entryId = jdbcTemplate.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = 1", Long.class);

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbcTemplate.queryForObject("SELECT ranking_position FROM contest_entry WHERE id = ?", Integer.class, entryId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT ballot_closed_at FROM motto_show WHERE id = 1", String.class)).isNull();
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot (motto_show_id, snapshot_number, created_at, is_current)
                VALUES (1, 1, CURRENT_TIMESTAMP, 1)
                """);
        long snapshotId = jdbcTemplate.queryForObject("SELECT id FROM ballot_snapshot WHERE motto_show_id = 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot_item (ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (?, 1, ?, 'Snapshot Interpret', 'Snapshot Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                """, snapshotId, entryId);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ballot_snapshot (motto_show_id, snapshot_number, created_at, is_current)
                VALUES (1, 2, CURRENT_TIMESTAMP, 1)
                """)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO ballot_snapshot_item (ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (?, 16, NULL, 'X', 'Y', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                """, snapshotId)).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update("DELETE FROM contest_entry WHERE id = ?", entryId);
        assertThat(jdbcTemplate.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE ballot_snapshot_id = ?", Long.class, snapshotId)).isNull();
        assertThat(jdbcTemplate.queryForObject("SELECT artist_snapshot FROM ballot_snapshot_item WHERE ballot_snapshot_id = ?", String.class, snapshotId))
                .isEqualTo("Snapshot Interpret");
    }

    @Test
    void upgradesAnExistingP5DatabaseWithoutChangingEntriesRankingsOrSnapshots() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("p5-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p5-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Bestehender Teilnehmer', 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long participantId = jdbcTemplate.queryForObject("SELECT id FROM participant", Long.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, listened, relisten, ranking_position, participant_id, created_at, updated_at)
                VALUES (1, 'Bestehender Beitrag', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 1, 0, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId);
        long entryId = jdbcTemplate.queryForObject("SELECT id FROM contest_entry WHERE motto_show_id = 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot (motto_show_id, snapshot_number, created_at, is_current)
                VALUES (1, 1, CURRENT_TIMESTAMP, 1)
                """);
        long snapshotId = jdbcTemplate.queryForObject("SELECT id FROM ballot_snapshot WHERE motto_show_id = 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot_item (ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (?, 1, ?, 'Bestehender Beitrag', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                """, snapshotId, entryId);

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbcTemplate.queryForObject("SELECT ranking_position FROM contest_entry WHERE id = ?", Integer.class, entryId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT participant_id FROM contest_entry WHERE id = ?", Long.class, entryId)).isEqualTo(participantId);
        assertThat(jdbcTemplate.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE ballot_snapshot_id = ?", Long.class, snapshotId))
                .isEqualTo(entryId);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM received_score", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT final_place_tied FROM motto_show WHERE id = 1", Integer.class)).isZero();
    }

    @Test
    void upgradesAnExistingV7DatabaseToIndependentConstrainedPoolPositions() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("v7-pool-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p6-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Bestehender Teilnehmer', 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long participantId = jdbcTemplate.queryForObject("SELECT id FROM participant", Long.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  ranking_position, participant_id, created_at, updated_at)
                VALUES (41, 1, 'Erster', 'A', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'Notiz', 1, 0,
                  NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  ranking_position, participant_id, created_at, updated_at)
                VALUES (77, 1, 'Zweiter', 'B', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, 0, 1,
                  1, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  ranking_position, participant_id, created_at, updated_at)
                VALUES (52, 2, 'Andere Show', 'C', 'https://www.youtube.com/watch?v=2Dqu1Gh45qU', NULL, 0, 0,
                  NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot (motto_show_id, snapshot_number, created_at, is_current)
                VALUES (1, 1, CURRENT_TIMESTAMP, 1)
                """);
        long snapshotId = jdbcTemplate.queryForObject("SELECT id FROM ballot_snapshot WHERE motto_show_id = 1", Long.class);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot_item (ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (?, 1, 77, 'Zweiter', 'B', 'https://www.youtube.com/watch?v=9bZkp7q19f0')
                """, snapshotId);

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbcTemplate.queryForList("SELECT id, pool_position FROM contest_entry WHERE motto_show_id = 1 ORDER BY pool_position"))
                .extracting(row -> ((Number) row.get("id")).longValue(), row -> ((Number) row.get("pool_position")).longValue())
                .containsExactly(tuple(41L, 1L), tuple(77L, 2L));
        assertThat(jdbcTemplate.queryForObject("SELECT ranking_position FROM contest_entry WHERE id = 77", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT assessment FROM contest_entry WHERE id = 41", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT assessment_confidence FROM contest_entry WHERE id = 41", Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT comment FROM contest_entry WHERE id = 41", String.class)).isEqualTo("Notiz");
        assertThat(jdbcTemplate.queryForObject("SELECT participant_id FROM contest_entry WHERE id = 41", Long.class)).isEqualTo(participantId);
        assertThat(jdbcTemplate.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE id = 1", Long.class)).isEqualTo(77L);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET pool_position = 0 WHERE id = 41"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET pool_position = 1 WHERE id = 77"))
                .isInstanceOf(DataAccessException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class)).isZero();
    }

    @Test
    void migratesActualSchema8EntriesToPairedAssessmentsWithoutLosingRelatedData() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("v8-assessment-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p8-master.yaml");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                VALUES (90, 'Bestehender Teilnehmer', 'DE', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  pool_position, ranking_position, participant_id, created_at, updated_at)
                VALUES (41, 1, 'Ungehört', 'A', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'Notiz A', 0, 1,
                  2, NULL, 90, '2026-01-02T00:00:00Z', '2026-01-02T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  pool_position, ranking_position, participant_id, created_at, updated_at)
                VALUES (42, 1, 'Unsicher', 'B', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, 1, 1,
                  1, 1, NULL, '2026-01-03T00:00:00Z', '2026-01-03T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                  pool_position, ranking_position, participant_id, created_at, updated_at)
                VALUES (43, 1, 'Sicherer', 'C', 'https://www.youtube.com/watch?v=2Dqu1Gh45qU', NULL, 1, 0,
                  3, NULL, NULL, '2026-01-04T00:00:00Z', '2026-01-04T00:00:00Z')
                """);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot (id, motto_show_id, snapshot_number, created_at, is_current)
                VALUES (100, 1, 1, '2026-01-05T00:00:00Z', 1)
                """);
        jdbcTemplate.update("""
                INSERT INTO ballot_snapshot_item (id, ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (101, 100, 1, 42, 'Unsicher', 'B', 'https://www.youtube.com/watch?v=9bZkp7q19f0')
                """);

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbcTemplate.queryForList("""
                SELECT id, assessment, assessment_confidence, pool_position, ranking_position, participant_id, comment, created_at, updated_at
                FROM contest_entry WHERE motto_show_id = 1 ORDER BY id
                """)).extracting(
                row -> ((Number) row.get("id")).longValue(),
                row -> row.get("assessment") == null ? null : ((Number) row.get("assessment")).longValue(),
                row -> row.get("assessment_confidence") == null ? null : ((Number) row.get("assessment_confidence")).longValue(),
                row -> ((Number) row.get("pool_position")).longValue(),
                row -> row.get("ranking_position") == null ? null : ((Number) row.get("ranking_position")).longValue(),
                row -> row.get("participant_id") == null ? null : ((Number) row.get("participant_id")).longValue(),
                row -> row.get("comment"),
                row -> row.get("created_at"),
                row -> row.get("updated_at")
        ).containsExactly(
                tuple(41L, null, null, 2L, null, 90L, "Notiz A", "2026-01-02T00:00:00Z", "2026-01-02T00:00:00Z"),
                tuple(42L, 3L, 1L, 1L, 1L, null, null, "2026-01-03T00:00:00Z", "2026-01-03T00:00:00Z"),
                tuple(43L, 3L, 2L, 3L, null, null, null, "2026-01-04T00:00:00Z", "2026-01-04T00:00:00Z")
        );
        assertThat(jdbcTemplate.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE id = 101", Long.class)).isEqualTo(42L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pragma_table_info('contest_entry') WHERE name IN ('listened', 'relisten')", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class)).isZero();
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET assessment = 4, assessment_confidence = NULL WHERE id = 42"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET assessment = 6, assessment_confidence = 3 WHERE id = 42"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET pool_position = 1 WHERE id = 41"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE contest_entry SET participant_id = 90 WHERE id = 42"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void enforcesP6AssignmentAndReceivedScoreConstraintsInSQLite() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("p6-constraints.db"));
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("""
                INSERT INTO participant (display_name, country_code, active, created_at, updated_at)
                VALUES ('Teilnehmer', 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
        long participantId = jdbcTemplate.queryForObject("SELECT id FROM participant", Long.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, participant_id, created_at, updated_at)
                VALUES (1, 'Beitrag', 'Titel', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', NULL, NULL, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, participant_id, created_at, updated_at)
                VALUES (1, 'Doppelung', 'Titel', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, 2, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId)).isInstanceOf(DataAccessException.class);
        jdbcTemplate.update("""
                INSERT INTO contest_entry (motto_show_id, artist, title, youtube_url, assessment, assessment_confidence, pool_position, participant_id, created_at, updated_at)
                VALUES (2, 'Andere Show', 'Titel', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId);
        jdbcTemplate.update("""
                INSERT INTO received_score (motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (1, ?, 'ABGESTIMMT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO received_score (motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (2, ?, 'ABGESTIMMT', 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO received_score (motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (2, ?, 'NICHT_ABGESTIMMT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE motto_show SET final_place = 0 WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE motto_show SET official_total_points = -1 WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE motto_show SET final_place_tied = 2 WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE motto_show SET final_place_tied = 1 WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO received_score (motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (2, ?, 'UNBEKANNT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO received_score (motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (2, ?, 'UNGUELTIG', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, participantId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM participant WHERE id = ?", participantId))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM motto_show WHERE id = 1"))
                .isInstanceOf(DataAccessException.class);
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
