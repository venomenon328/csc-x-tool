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
                selectedCandidate(resultSet),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
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
