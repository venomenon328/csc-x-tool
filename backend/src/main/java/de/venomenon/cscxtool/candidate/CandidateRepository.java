package de.venomenon.cscxtool.candidate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class CandidateRepository {

    private static final RowMapper<Candidate> ROW_MAPPER = CandidateRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    CandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean showExists(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE id = ?)", Boolean.class, showId
        ));
    }

    List<Candidate> findAllByShowId(long showId) {
        return jdbcTemplate.query("""
                SELECT id, motto_show_id, artist, title, youtube_url, comment, status, manual_position, created_at, updated_at
                FROM candidate
                WHERE motto_show_id = ?
                ORDER BY manual_position
                """, ROW_MAPPER, showId);
    }

    Optional<Candidate> findByIdAndShowId(long candidateId, long showId) {
        return jdbcTemplate.query("""
                SELECT id, motto_show_id, artist, title, youtube_url, comment, status, manual_position, created_at, updated_at
                FROM candidate
                WHERE id = ? AND motto_show_id = ?
                """, ROW_MAPPER, candidateId, showId).stream().findFirst();
    }

    Candidate append(long showId, String artist, String title, String youtubeUrl, String comment, CandidateStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO candidate (
                      motto_show_id, artist, title, youtube_url, comment, status, manual_position, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?,
                      (SELECT COALESCE(MAX(manual_position), 0) + 1 FROM candidate WHERE motto_show_id = ?),
                      CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setString(2, artist);
            statement.setString(3, title);
            statement.setString(4, youtubeUrl);
            statement.setString(5, comment);
            statement.setString(6, status.name());
            statement.setLong(7, showId);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new candidate.");
        }
        return findByIdAndShowId(generatedId.longValue(), showId).orElseThrow();
    }

    boolean update(long candidateId, long showId, String artist, String title, String youtubeUrl, String comment, CandidateStatus status) {
        return jdbcTemplate.update("""
                UPDATE candidate
                SET artist = ?, title = ?, youtube_url = ?, comment = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, artist, title, youtubeUrl, comment, status.name(), candidateId, showId) == 1;
    }

    boolean delete(long candidateId, long showId) {
        return jdbcTemplate.update("DELETE FROM candidate WHERE id = ? AND motto_show_id = ?", candidateId, showId) == 1;
    }

    void closePositionGap(long showId, int formerPosition) {
        int offset = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM candidate WHERE motto_show_id = ?", Integer.class, showId
        );
        // The temporary offset prevents SQLite's immediate UNIQUE check from seeing a duplicate
        // while positions are compacted (for example 3 -> 2 while another row is still at 2).
        jdbcTemplate.update("""
                UPDATE candidate
                SET manual_position = manual_position + ?, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ? AND manual_position > ?
                """, offset, showId, formerPosition);
        jdbcTemplate.update("""
                UPDATE candidate
                SET manual_position = manual_position - ?, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ? AND manual_position > ?
                """, offset + 1, showId, formerPosition + offset);
    }

    void reorder(long showId, List<Long> candidateIds) {
        int offset = candidateIds.size();
        jdbcTemplate.update("""
                UPDATE candidate
                SET manual_position = manual_position + ?, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ?
                """, offset, showId);
        for (int index = 0; index < candidateIds.size(); index++) {
            jdbcTemplate.update("""
                    UPDATE candidate
                    SET manual_position = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND motto_show_id = ?
                    """, index + 1, candidateIds.get(index), showId);
        }
    }

    boolean isSelectedForShow(long candidateId, long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM motto_show WHERE id = ? AND selected_candidate_id = ?
                )
                """, Boolean.class, showId, candidateId));
    }

    Long selectedCandidateId(long showId) {
        List<Long> values = jdbcTemplate.query("SELECT selected_candidate_id FROM motto_show WHERE id = ?", (resultSet, row) -> {
            long value = resultSet.getLong(1);
            return resultSet.wasNull() ? null : value;
        }, showId);
        return values.isEmpty() ? null : values.getFirst();
    }

    boolean resultsClosed(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT results_closed_at IS NOT NULL FROM motto_show WHERE id = ?", Boolean.class, showId
        ));
    }

    void selectSubmission(long showId, long candidateId) {
        jdbcTemplate.update("""
                UPDATE motto_show
                SET selected_candidate_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, candidateId, showId);
    }

    void clearSubmission(long showId) {
        jdbcTemplate.update("""
                UPDATE motto_show
                SET selected_candidate_id = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, showId);
    }

    List<Long> existingShowIds(Collection<Long> showIds) {
        if (showIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(showIds.size(), "?"));
        return jdbcTemplate.query(
                "SELECT id FROM motto_show WHERE id IN (" + placeholders + ")",
                (resultSet, row) -> resultSet.getLong("id"),
                showIds.toArray()
        );
    }

    private static Candidate mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Candidate(
                resultSet.getLong("id"),
                resultSet.getLong("motto_show_id"),
                resultSet.getString("artist"),
                resultSet.getString("title"),
                resultSet.getString("youtube_url"),
                resultSet.getString("comment"),
                CandidateStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("manual_position"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }
}
