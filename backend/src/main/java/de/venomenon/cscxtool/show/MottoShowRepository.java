package de.venomenon.cscxtool.show;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class MottoShowRepository {

    private static final RowMapper<MottoShow> ROW_MAPPER = MottoShowRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    MottoShowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<MottoShow> findAll() {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.show_number, motto_show.name,
                       (SELECT COUNT(*) FROM candidate WHERE candidate.motto_show_id = motto_show.id) AS candidate_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id) AS contest_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.listened = 1) AS listened_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.ranking_position IS NOT NULL) AS ranked_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.participant_id IS NOT NULL) AS assigned_entry_count,
                       (SELECT COUNT(*) FROM participant WHERE participant.active = 1) AS active_participant_count,
                       (SELECT COUNT(*) FROM participant
                        WHERE participant.active = 1 AND EXISTS (
                          SELECT 1 FROM received_score
                          WHERE received_score.motto_show_id = motto_show.id
                            AND received_score.participant_id = participant.id
                            AND received_score.status <> 'UNBEKANNT'
                        )) AS known_active_result_count,
                       motto_show.ballot_closed_at,
                       motto_show.results_closed_at,
                       (SELECT COALESCE(SUM(points), 0) FROM received_score
                        WHERE received_score.motto_show_id = motto_show.id AND status = 'ABGESTIMMT') AS calculated_total_points,
                       motto_show.official_total_points, motto_show.final_place, motto_show.final_place_tied,
                       selected_candidate.id AS selected_candidate_id,
                       selected_candidate.artist AS selected_candidate_artist,
                       selected_candidate.title AS selected_candidate_title,
                       selected_candidate.youtube_url AS selected_candidate_youtube_url,
                       motto_show.created_at, motto_show.updated_at
                FROM motto_show
                LEFT JOIN candidate AS selected_candidate ON selected_candidate.id = motto_show.selected_candidate_id
                ORDER BY motto_show.show_number
                """, ROW_MAPPER);
    }

    Optional<MottoShow> findById(long id) {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.show_number, motto_show.name,
                       (SELECT COUNT(*) FROM candidate WHERE candidate.motto_show_id = motto_show.id) AS candidate_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id) AS contest_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.listened = 1) AS listened_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.ranking_position IS NOT NULL) AS ranked_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.participant_id IS NOT NULL) AS assigned_entry_count,
                       (SELECT COUNT(*) FROM participant WHERE participant.active = 1) AS active_participant_count,
                       (SELECT COUNT(*) FROM participant
                        WHERE participant.active = 1 AND EXISTS (
                          SELECT 1 FROM received_score
                          WHERE received_score.motto_show_id = motto_show.id
                            AND received_score.participant_id = participant.id
                            AND received_score.status <> 'UNBEKANNT'
                        )) AS known_active_result_count,
                       motto_show.ballot_closed_at,
                       motto_show.results_closed_at,
                       (SELECT COALESCE(SUM(points), 0) FROM received_score
                        WHERE received_score.motto_show_id = motto_show.id AND status = 'ABGESTIMMT') AS calculated_total_points,
                       motto_show.official_total_points, motto_show.final_place, motto_show.final_place_tied,
                       selected_candidate.id AS selected_candidate_id,
                       selected_candidate.artist AS selected_candidate_artist,
                       selected_candidate.title AS selected_candidate_title,
                       selected_candidate.youtube_url AS selected_candidate_youtube_url,
                       motto_show.created_at, motto_show.updated_at
                FROM motto_show
                LEFT JOIN candidate AS selected_candidate ON selected_candidate.id = motto_show.selected_candidate_id
                WHERE motto_show.id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    boolean rename(long id, String name) {
        return jdbcTemplate.update("""
                UPDATE motto_show
                SET name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, name, id) == 1;
    }

    private static MottoShow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MottoShow(
                resultSet.getLong("id"),
                resultSet.getInt("show_number"),
                resultSet.getString("name"),
                resultSet.getInt("candidate_count"),
                resultSet.getInt("contest_entry_count"),
                resultSet.getInt("listened_entry_count"),
                resultSet.getInt("ranked_entry_count"),
                resultSet.getInt("assigned_entry_count"),
                resultSet.getInt("active_participant_count"),
                resultSet.getInt("known_active_result_count"),
                nullableInstant(resultSet, "ballot_closed_at"),
                nullableInstant(resultSet, "results_closed_at"),
                resultSet.getInt("calculated_total_points"),
                nullableInt(resultSet, "official_total_points"),
                nullableInt(resultSet, "final_place"),
                resultSet.getBoolean("final_place_tied"),
                selectedCandidate(resultSet),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Instant nullableInstant(ResultSet resultSet, String columnName) throws SQLException {
        java.sql.Timestamp timestamp = resultSet.getTimestamp(columnName);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Integer nullableInt(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static SelectedCandidate selectedCandidate(ResultSet resultSet) throws SQLException {
        long selectedCandidateId = resultSet.getLong("selected_candidate_id");
        if (resultSet.wasNull()) {
            return null;
        }
        return new SelectedCandidate(
                selectedCandidateId,
                resultSet.getString("selected_candidate_artist"),
                resultSet.getString("selected_candidate_title"),
                resultSet.getString("selected_candidate_youtube_url")
        );
    }
}
