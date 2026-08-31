package de.venomenon.cscxtool.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class LiquibaseMigrationIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void migratesAnEmptySqliteFileToOneCurrentCscXContest() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("empty.db"));
        migrate(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList("SELECT id, name, display_order, is_current FROM contest"))
                .extracting(row -> ((Number) row.get("id")).longValue(), row -> row.get("name"),
                        row -> ((Number) row.get("display_order")).longValue(), row -> ((Number) row.get("is_current")).longValue())
                .containsExactly(tuple(1L, "CSC X", 1L, 1L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM motto_show WHERE contest_id = 1", Integer.class)).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_table_info('participant') WHERE name = 'country_code'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_table_info('motto_show') WHERE name = 'official_total_points'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'received_score'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class)).isZero();
    }

    @Test
    void upgradesARealisticImmediatePredecessorDatabaseWithoutLosingIdsOrStates() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("p9-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p9-master.yaml");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        jdbc.update("UPDATE motto_show SET name = 'Erhaltene Show' WHERE id = 1");
        jdbc.update("""
                INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                VALUES (1, 'Unbekannt', 'DK', 1, '2026-01-03T00:00:00Z', '2026-01-04T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                VALUES (90, 'Aktiv und zugeordnet', 'DE', 1, '2026-01-01T00:00:00Z', '2026-01-02T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                VALUES (91, 'Historisch inaktiv', 'AT', 0, '2026-01-03T00:00:00Z', '2026-01-04T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at)
                VALUES (93, 'Null Punkte', 'CH', 1, '2026-01-03T00:00:00Z', '2026-01-04T00:00:00Z')
                """);
        jdbc.update("INSERT INTO participant_alias (id, participant_id, alias) VALUES (92, 90, 'Alias bleibt')");
        jdbc.update("""
                INSERT INTO candidate (id, motto_show_id, artist, title, youtube_url, comment, status, manual_position, created_at, updated_at)
                VALUES (70, 1, 'Eigene Band', 'Eigener Song', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ',
                  'Auswahl bleibt', 'FINALIST', 1, '2026-01-05T00:00:00Z', '2026-01-06T00:00:00Z')
                """);
        jdbc.update("""
                UPDATE motto_show SET selected_candidate_id = 70, ballot_closed_at = '2026-01-07T00:00:00Z',
                  results_closed_at = '2026-01-17T00:00:00Z', official_total_points = 38, final_place = 4, final_place_tied = 1
                WHERE id = 1
                """);
        jdbc.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, assessment, assessment_confidence,
                  pool_position, ranking_position, participant_id, created_at, updated_at)
                VALUES (41, 1, 'Zugeordnete Band', 'A', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', 'Notiz A', 4, 2,
                  1, 1, 90, '2026-01-08T00:00:00Z', '2026-01-09T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, assessment, assessment_confidence,
                  pool_position, ranking_position, participant_id, created_at, updated_at)
                VALUES (42, 1, 'Unzugeordnete Band', 'B', 'https://www.youtube.com/watch?v=9bZkp7q19f0', NULL, NULL, NULL,
                  2, NULL, NULL, '2026-01-10T00:00:00Z', '2026-01-11T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO ballot_snapshot (id, motto_show_id, snapshot_number, created_at, is_current)
                VALUES (100, 1, 3, '2026-01-12T00:00:00Z', 1)
                """);
        jdbc.update("""
                INSERT INTO ballot_snapshot_item (id, ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                VALUES (101, 100, 1, 41, 'Zugeordnete Band', 'A', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ')
                """);
        jdbc.update("""
                INSERT INTO received_score (id, motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (150, 1, 90, 'ABGESTIMMT', 13, '2026-01-13T00:00:00Z', '2026-01-14T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO received_score (id, motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (151, 1, 91, 'NICHT_ABGESTIMMT', NULL, '2026-01-15T00:00:00Z', '2026-01-16T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO received_score (id, motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (152, 1, 1, 'UNBEKANNT', NULL, '2026-01-15T00:00:00Z', '2026-01-16T00:00:00Z')
                """);
        jdbc.update("""
                INSERT INTO received_score (id, motto_show_id, participant_id, status, points, created_at, updated_at)
                VALUES (153, 1, 93, 'ABGESTIMMT', 0, '2026-01-15T00:00:00Z', '2026-01-16T00:00:00Z')
                """);

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbc.queryForObject("SELECT name FROM motto_show WHERE id = 1", String.class)).isEqualTo("Erhaltene Show");
        assertThat(jdbc.queryForObject("SELECT contest_id FROM motto_show WHERE id = 1", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT selected_candidate_id FROM motto_show WHERE id = 1", Long.class)).isEqualTo(70L);
        assertThat(jdbc.queryForObject("SELECT own_entry_resolution FROM motto_show WHERE id = 1", String.class)).isEqualTo("UNRESOLVED");
        assertThat(jdbc.queryForList("""
                SELECT id, participant_id, country_code, active, created_at, updated_at
                FROM contest_participation ORDER BY id
                """)).extracting(
                row -> ((Number) row.get("id")).longValue(), row -> ((Number) row.get("participant_id")).longValue(),
                row -> row.get("country_code"), row -> ((Number) row.get("active")).longValue(),
                row -> row.get("created_at"), row -> row.get("updated_at")
        ).containsExactly(
                tuple(1L, 1L, "DK", 1L, "2026-01-03T00:00:00Z", "2026-01-04T00:00:00Z"),
                tuple(90L, 90L, "DE", 1L, "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z"),
                tuple(91L, 91L, "AT", 0L, "2026-01-03T00:00:00Z", "2026-01-04T00:00:00Z"),
                tuple(93L, 93L, "CH", 1L, "2026-01-03T00:00:00Z", "2026-01-04T00:00:00Z")
        );
        assertThat(jdbc.queryForList("""
                SELECT id, motto_show_id, contest_id, contest_participation_id, assessment, assessment_confidence,
                  pool_position, ranking_position, comment, created_at, updated_at
                FROM contest_entry WHERE id IN (41, 42) ORDER BY id
                """)).extracting(
                row -> ((Number) row.get("id")).longValue(), row -> ((Number) row.get("motto_show_id")).longValue(),
                row -> ((Number) row.get("contest_id")).longValue(),
                row -> row.get("contest_participation_id") == null ? null : ((Number) row.get("contest_participation_id")).longValue(),
                row -> row.get("assessment") == null ? null : ((Number) row.get("assessment")).longValue(),
                row -> row.get("assessment_confidence") == null ? null : ((Number) row.get("assessment_confidence")).longValue(),
                row -> ((Number) row.get("pool_position")).longValue(),
                row -> row.get("ranking_position") == null ? null : ((Number) row.get("ranking_position")).longValue(),
                row -> row.get("comment"), row -> row.get("created_at"), row -> row.get("updated_at")
        ).containsExactly(
                tuple(41L, 1L, 1L, 90L, 4L, 2L, 1L, 1L, "Notiz A", "2026-01-08T00:00:00Z", "2026-01-09T00:00:00Z"),
                tuple(42L, 1L, 1L, null, null, null, 2L, null, null, "2026-01-10T00:00:00Z", "2026-01-11T00:00:00Z")
        );
        assertThat(jdbc.queryForList("SELECT id, contest_id, contest_participation_id, status, points FROM legacy_received_score ORDER BY id"))
                .extracting(row -> ((Number) row.get("id")).longValue(), row -> ((Number) row.get("contest_id")).longValue(),
                        row -> ((Number) row.get("contest_participation_id")).longValue(), row -> row.get("status"),
                        row -> row.get("points") == null ? null : ((Number) row.get("points")).longValue())
                .containsExactly(tuple(150L, 1L, 90L, "ABGESTIMMT", 13L), tuple(151L, 1L, 91L, "NICHT_ABGESTIMMT", null), tuple(152L, 1L, 1L, "UNBEKANNT", null), tuple(153L, 1L, 93L, "ABGESTIMMT", 0L));
        assertThat(jdbc.queryForList("SELECT results_closed_at, official_total_points, final_place, final_place_tied FROM legacy_result WHERE motto_show_id = 1"))
                .extracting(row -> row.get("results_closed_at"), row -> ((Number) row.get("official_total_points")).longValue(),
                        row -> ((Number) row.get("final_place")).longValue(), row -> ((Number) row.get("final_place_tied")).longValue())
                .containsExactly(tuple("2026-01-17T00:00:00Z", 38L, 4L, 1L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT contest_entry_id FROM ballot_snapshot_item WHERE id = 101", Long.class)).isEqualTo(41L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_table_info('participant') WHERE name = 'country_code'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class)).isZero();
    }

    @Test
    void archivesTheActualP12ResultModelWithoutChangingPublishedBallotsOrSnapshots() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("p12-upgrade.db");
        DataSource dataSource = SqliteDataSourceFactory.create(databaseFile);
        migrate(dataSource, "classpath:/db/changelog/p12-master.yaml");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (20,'Ich',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (21,'Wertend',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (20,1,20,'DE',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at) VALUES (21,1,21,'AT',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO candidate (id,motto_show_id,artist,title,youtube_url,status,manual_position,created_at,updated_at) VALUES (30,1,'Plan','Song','https://example.test/plan','FINALIST',1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        jdbc.update("UPDATE motto_show SET selected_candidate_id=30,entry_list_complete=1,results_closed_at=CURRENT_TIMESTAMP,official_total_points=42,final_place=2,final_place_tied=1 WHERE id=1");
        for (int index = 0; index < 16; index++) {
            jdbc.update("""
                    INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,pool_position,contest_participation_id,created_at,updated_at)
                    VALUES (?,1,1,?,?,?, ?, ?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, 100 + index, "Band " + index, "Song " + index, "https://example.test/" + index,
                    index + 1, index == 0 ? 20 : null);
        }
        jdbc.update("INSERT INTO ballot_snapshot (id,motto_show_id,snapshot_number,created_at,is_current) VALUES (40,1,1,CURRENT_TIMESTAMP,1)");
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("INSERT INTO ballot_snapshot_item (ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot) VALUES (40,?,?,?,?,'https://example.test/snapshot')",
                    rank, 100 + rank, "Snapshot " + rank, "Titel " + rank);
        }
        jdbc.update("INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at) VALUES (50,1,1,21,'ABGESTIMMT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
        for (int rank = 1; rank <= 15; rank++) {
            jdbc.update("INSERT INTO published_ballot_position (published_ballot_id,contest_entry_id,rank) VALUES (50,?,?)", 99 + rank, rank);
        }
        jdbc.update("INSERT INTO received_score (id,motto_show_id,contest_id,contest_participation_id,status,points,created_at,updated_at) VALUES (60,1,1,21,'ABGESTIMMT',0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");

        migrate(SqliteDataSourceFactory.create(databaseFile));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot WHERE id=50", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM published_ballot_position WHERE published_ballot_id=50", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ballot_snapshot_item WHERE ballot_snapshot_id=40", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT points FROM legacy_received_score WHERE id=60", Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT official_total_points,final_place,final_place_tied FROM legacy_result WHERE motto_show_id=1"))
                .extracting(row -> ((Number) row.get("official_total_points")).longValue(), row -> ((Number) row.get("final_place")).longValue(),
                        row -> ((Number) row.get("final_place_tied")).longValue())
                .containsExactly(tuple(42L, 2L, 1L));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_table_info('motto_show') WHERE name IN ('results_closed_at','official_total_points','final_place','final_place_tied')", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='received_score'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM pragma_foreign_key_check", Integer.class)).isZero();
    }

    @Test
    void preventsContestForeignEntryAndScoreAssignmentsAtDatabaseLevel() throws Exception {
        DataSource dataSource = SqliteDataSourceFactory.create(temporaryDirectory.resolve("contest-boundaries.db"));
        migrate(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("INSERT INTO participant (id, display_name, active, created_at, updated_at) VALUES (90, 'Teilnehmer', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest (id, name, display_order, is_current, created_at, updated_at) VALUES (2, 'CSC Y', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO motto_show (id, contest_id, show_number, name, created_at, updated_at) VALUES (20, 2, 1, 'Andere Ausgabe', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        jdbc.update("INSERT INTO contest_participation (id, contest_id, participant_id, country_code, active, created_at, updated_at) VALUES (90, 2, 90, 'DE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO contest_entry (motto_show_id, contest_id, artist, title, youtube_url, assessment, assessment_confidence,
                  pool_position, contest_participation_id, created_at, updated_at)
                VALUES (1, 2, 'Fremd', 'Zuordnung', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', NULL, NULL, 1, 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """)).isInstanceOf(DataAccessException.class);
        jdbc.update("""
                INSERT INTO contest_entry (motto_show_id, contest_id, artist, title, youtube_url, assessment, assessment_confidence,
                  pool_position, contest_participation_id, created_at, updated_at)
                VALUES (20, 2, 'Passend', 'Zuordnung', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', NULL, NULL, 1, 90, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);
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
