package de.venomenon.cscxtool.entry;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class ContestEntryRepository {

    private static final RowMapper<ContestEntry> ROW_MAPPER = ContestEntryRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    ContestEntryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean showExists(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE id = ?)", Boolean.class, showId
        ));
    }

    List<ContestEntry> findAllByShowId(long showId) {
        return jdbcTemplate.query(selectSql("WHERE motto_show_id = ? ORDER BY pool_position"), ROW_MAPPER, showId);
    }

    Optional<ContestEntry> findByIdAndShowId(long entryId, long showId) {
        return jdbcTemplate.query(selectSql("WHERE id = ? AND motto_show_id = ?"), ROW_MAPPER, entryId, showId)
                .stream().findFirst();
    }

    ContestEntry create(long showId, String artist, String title, String youtubeUrl, String comment) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contest_entry (
                      motto_show_id, artist, title, youtube_url, comment, assessment, assessment_confidence,
                      pool_position, ranking_position, participant_id, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, NULL, NULL,
                      (SELECT COALESCE(MAX(pool_position), 0) + 1 FROM contest_entry WHERE motto_show_id = ?),
                      NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setString(2, artist);
            statement.setString(3, title);
            statement.setString(4, youtubeUrl);
            statement.setString(5, comment);
            statement.setLong(6, showId);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new contest entry.");
        }
        return findByIdAndShowId(generatedId.longValue(), showId).orElseThrow();
    }

    boolean update(
            long entryId,
            long showId,
            String artist,
            String title,
            String youtubeUrl,
            String comment
    ) {
        return jdbcTemplate.update("""
                UPDATE contest_entry
                SET artist = ?, title = ?, youtube_url = ?, comment = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, artist, title, youtubeUrl, comment, entryId, showId) == 1;
    }

    boolean updateAssessment(long entryId, long showId, Integer assessment, Integer assessmentConfidence) {
        return jdbcTemplate.update("""
                UPDATE contest_entry
                SET assessment = ?, assessment_confidence = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, assessment, assessmentConfidence, entryId, showId) == 1;
    }

    boolean delete(long entryId, long showId) {
        return jdbcTemplate.update("DELETE FROM contest_entry WHERE id = ? AND motto_show_id = ?", entryId, showId) == 1;
    }

    boolean isBallotClosed(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT ballot_closed_at IS NOT NULL FROM motto_show WHERE id = ?", Boolean.class, showId
        ));
    }

    boolean participantExists(long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM participant WHERE id = ?)", Boolean.class, participantId
        ));
    }

    boolean participantIsActive(long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT active FROM participant WHERE id = ?", Boolean.class, participantId
        ));
    }

    Optional<Long> findEntryIdByParticipant(long showId, long participantId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND participant_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"), showId, participantId
        ).stream().findFirst();
    }

    boolean updateParticipantAssignment(long entryId, long showId, Long participantId) {
        return jdbcTemplate.update("""
                UPDATE contest_entry
                SET participant_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, participantId, entryId, showId) == 1;
    }

    List<Long> findRankedEntryIds(long showId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND ranking_position IS NOT NULL ORDER BY ranking_position",
                (resultSet, rowNumber) -> resultSet.getLong(1), showId
        );
    }

    List<Long> findPoolEntryIds(long showId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? ORDER BY pool_position",
                (resultSet, rowNumber) -> resultSet.getLong(1), showId
        );
    }

    void replacePool(long showId, List<Long> entryIds) {
        // A direct reassignment can violate SQLite's unique constraint while two positions swap.
        // Move the complete order out of its value range first, then write the final values.
        Integer offset = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(pool_position), 0) + 1 FROM contest_entry WHERE motto_show_id = ?",
                Integer.class,
                showId
        );
        if (offset == null) {
            throw new IllegalStateException("SQLite did not calculate a pool ordering offset.");
        }
        jdbcTemplate.update("""
                UPDATE contest_entry
                SET pool_position = pool_position + ?, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ?
                """, offset, showId);
        for (int index = 0; index < entryIds.size(); index++) {
            int changed = jdbcTemplate.update("""
                    UPDATE contest_entry
                    SET pool_position = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND motto_show_id = ?
                    """, index + 1, entryIds.get(index), showId);
            if (changed != 1) {
                throw new IllegalStateException("A validated contest entry disappeared during pool ordering replacement.");
            }
        }
    }

    void replaceRanking(long showId, List<Long> rankedEntryIds) {
        jdbcTemplate.update("""
                UPDATE contest_entry
                SET ranking_position = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE motto_show_id = ?
                """, showId);
        for (int index = 0; index < rankedEntryIds.size(); index++) {
            int changed = jdbcTemplate.update("""
                    UPDATE contest_entry
                    SET ranking_position = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND motto_show_id = ?
                    """, index + 1, rankedEntryIds.get(index), showId);
            if (changed != 1) {
                throw new IllegalStateException("A contest entry disappeared while closing a ranking gap.");
            }
        }
    }

    private static String selectSql(String whereClause) {
        return """
                SELECT id, motto_show_id, artist, title, youtube_url, comment, assessment, assessment_confidence,
                       pool_position, ranking_position, participant_id, created_at, updated_at
                FROM contest_entry
                """ + whereClause;
    }

    private static ContestEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        int rankingPosition = resultSet.getInt("ranking_position");
        Integer nullableRankingPosition = resultSet.wasNull() ? null : rankingPosition;
        long participantId = resultSet.getLong("participant_id");
        Long nullableParticipantId = resultSet.wasNull() ? null : participantId;
        return new ContestEntry(
                resultSet.getLong("id"),
                resultSet.getLong("motto_show_id"),
                resultSet.getString("artist"),
                resultSet.getString("title"),
                resultSet.getString("youtube_url"),
                resultSet.getString("comment"),
                nullableInt(resultSet, "assessment"),
                nullableInt(resultSet, "assessment_confidence"),
                resultSet.getInt("pool_position"),
                nullableRankingPosition,
                nullableParticipantId,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Integer nullableInt(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
