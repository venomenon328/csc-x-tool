package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.show.ShowContext;
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
        return jdbcTemplate.query(selectSql("WHERE contest_entry.motto_show_id = ? ORDER BY contest_entry.pool_position"), ROW_MAPPER, showId);
    }

    Optional<ContestEntry> findByIdAndShowId(long entryId, long showId) {
        return jdbcTemplate.query(selectSql("WHERE contest_entry.id = ? AND contest_entry.motto_show_id = ?"), ROW_MAPPER, entryId, showId)
                .stream().findFirst();
    }

    ContestEntry create(long showId, String artist, String title, String youtubeUrl, String comment) {
        return create(showId, artist, title, youtubeUrl, comment, null);
    }

    ContestEntry create(long showId, String artist, String title, String youtubeUrl, String comment, Long participationId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contest_entry (
                      motto_show_id, contest_id, artist, title, youtube_url, comment, assessment, assessment_confidence,
                      pool_position, ranking_position, contest_participation_id, created_at, updated_at
                    ) VALUES (?, (SELECT contest_id FROM motto_show WHERE id = ?), ?, ?, ?, ?, NULL, NULL,
                      (SELECT COALESCE(MAX(pool_position), 0) + 1 FROM contest_entry WHERE motto_show_id = ?),
                      NULL, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setLong(2, showId);
            statement.setString(3, artist);
            statement.setString(4, title);
            statement.setString(5, youtubeUrl);
            statement.setString(6, comment);
            statement.setLong(7, showId);
            if (participationId == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, participationId);
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

    boolean updateHistorical(
            long entryId, long showId, String artist, String title, String youtubeUrl, String comment, long participationId
    ) {
        return jdbcTemplate.update("""
                UPDATE contest_entry
                SET artist = ?, title = ?, youtube_url = ?, comment = ?, contest_participation_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, artist, title, youtubeUrl, comment, participationId, entryId, showId) == 1;
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

    Optional<Long> findEntryIdByParticipation(long showId, long participationId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND contest_participation_id = ?",
                (resultSet, rowNumber) -> resultSet.getLong("id"), showId, participationId
        ).stream().findFirst();
    }

    boolean updateParticipantAssignment(long entryId, long showId, Long participationId) {
        return jdbcTemplate.update("""
                UPDATE contest_entry
                SET contest_participation_id = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND motto_show_id = ?
                """, participationId, entryId, showId) == 1;
    }

    List<Long> findRankedEntryIds(long showId) {
        return jdbcTemplate.query(
                "SELECT id FROM contest_entry WHERE motto_show_id = ? AND ranking_position IS NOT NULL ORDER BY ranking_position",
                (resultSet, rowNumber) -> resultSet.getLong(1), showId
        );
    }

    Optional<ShowContext> findShowContext(long showId) {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.contest_id, contest.is_current, motto_show.entry_list_complete
                FROM motto_show JOIN contest ON contest.id = motto_show.contest_id
                WHERE motto_show.id = ?
                """, (resultSet, rowNumber) -> new ShowContext(
                resultSet.getLong(1), resultSet.getLong(2), resultSet.getBoolean(3), resultSet.getBoolean(4)
        ), showId).stream().findFirst();
    }

    List<HistoricalImportParticipant> findHistoricalImportParticipants(long showId) {
        return jdbcTemplate.query("""
                SELECT participation.id, participation.participant_id, participant.display_name, participation.country_code,
                       COALESCE(group_concat(participant_alias.alias, char(31)), '')
                FROM motto_show
                JOIN contest_participation participation ON participation.contest_id = motto_show.contest_id
                JOIN participant ON participant.id = participation.participant_id
                LEFT JOIN participant_alias ON participant_alias.participant_id = participant.id
                WHERE motto_show.id = ?
                GROUP BY participation.id
                ORDER BY participant.display_name COLLATE NOCASE, participant.id
                """, (resultSet, rowNumber) -> new HistoricalImportParticipant(
                resultSet.getLong(1), resultSet.getLong(2), resultSet.getString(3), resultSet.getString(4),
                splitAliases(resultSet.getString(5))
        ), showId);
    }

    int historicalEntryCount(long showId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = ?", Integer.class, showId
        );
        return count == null ? 0 : count;
    }

    int unassignedHistoricalEntryCount(long showId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM contest_entry entry
                LEFT JOIN contest_participation participation ON participation.id = entry.contest_participation_id
                  AND participation.contest_id = entry.contest_id
                WHERE entry.motto_show_id = ? AND participation.id IS NULL
                """, Integer.class, showId);
        return count == null ? 0 : count;
    }

    boolean setEntryListComplete(long showId, boolean complete) {
        return jdbcTemplate.update("""
                UPDATE motto_show SET entry_list_complete = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, complete, showId) == 1;
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
                SELECT contest_entry.id, contest_entry.motto_show_id, contest_entry.contest_id,
                       contest_entry.artist, contest_entry.title, contest_entry.youtube_url, contest_entry.comment,
                       contest_entry.assessment, contest_entry.assessment_confidence, contest_entry.pool_position,
                       contest_entry.ranking_position, contest_entry.contest_participation_id,
                       participation.participant_id, contest_entry.created_at, contest_entry.updated_at
                FROM contest_entry
                LEFT JOIN contest_participation participation ON participation.id = contest_entry.contest_participation_id
                """ + whereClause;
    }

    private static ContestEntry mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        int rankingPosition = resultSet.getInt("ranking_position");
        Integer nullableRankingPosition = resultSet.wasNull() ? null : rankingPosition;
        long participationId = resultSet.getLong("contest_participation_id");
        Long nullableParticipationId = resultSet.wasNull() ? null : participationId;
        long participantId = resultSet.getLong("participant_id");
        Long nullableParticipantId = resultSet.wasNull() ? null : participantId;
        return new ContestEntry(
                resultSet.getLong("id"),
                resultSet.getLong("motto_show_id"),
                resultSet.getLong("contest_id"),
                resultSet.getString("artist"),
                resultSet.getString("title"),
                resultSet.getString("youtube_url"),
                resultSet.getString("comment"),
                nullableInt(resultSet, "assessment"),
                nullableInt(resultSet, "assessment_confidence"),
                resultSet.getInt("pool_position"),
                nullableRankingPosition,
                nullableParticipationId,
                nullableParticipantId,
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Integer nullableInt(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private static List<String> splitAliases(String aliases) {
        if (aliases == null || aliases.isBlank()) return List.of();
        return List.of(aliases.split(String.valueOf((char) 31)));
    }
}
