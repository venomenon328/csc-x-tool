package de.venomenon.cscxtool.show;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
class MottoShowRepository {

    private static final RowMapper<MottoShow> ROW_MAPPER = MottoShowRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    MottoShowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<MottoShow> findAll(long contestId) {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.contest_id, motto_show.show_number, motto_show.name, motto_show.entry_list_complete,
                       (SELECT COUNT(*) FROM candidate WHERE candidate.motto_show_id = motto_show.id) AS candidate_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id) AS contest_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.assessment IS NOT NULL) AS assessed_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.ranking_position IS NOT NULL) AS ranked_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.contest_participation_id IS NOT NULL) AS assigned_entry_count,
                       (SELECT COUNT(*) FROM contest_participation participation
                        WHERE participation.contest_id = motto_show.contest_id AND participation.active = 1) AS active_participant_count,
                       (SELECT COUNT(*) FROM published_ballot ballot
                        WHERE ballot.motto_show_id = motto_show.id AND ballot.status = 'ABGESTIMMT') AS published_ballot_voted_count,
                       (SELECT COUNT(*) FROM published_ballot ballot
                        WHERE ballot.motto_show_id = motto_show.id AND ballot.status = 'NICHT_ABGESTIMMT') AS published_ballot_not_voted_count,
                       (SELECT COUNT(*) FROM contest_participation participation
                        WHERE participation.contest_id = motto_show.contest_id AND NOT EXISTS (
                          SELECT 1 FROM published_ballot ballot
                          WHERE ballot.motto_show_id = motto_show.id AND ballot.contest_participation_id = participation.id
                        )) AS published_ballot_unrecorded_count,
                       motto_show.ballot_closed_at,
                       selected_candidate.id AS selected_candidate_id,
                       selected_candidate.artist AS selected_candidate_artist,
                       selected_candidate.title AS selected_candidate_title,
                       selected_candidate.youtube_url AS selected_candidate_youtube_url,
                       motto_show.created_at, motto_show.updated_at
                FROM motto_show
                LEFT JOIN candidate AS selected_candidate ON selected_candidate.id = motto_show.selected_candidate_id
                WHERE motto_show.contest_id = ?
                ORDER BY motto_show.show_number
                """, ROW_MAPPER, contestId);
    }

    Optional<MottoShow> findById(long id) {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.contest_id, motto_show.show_number, motto_show.name, motto_show.entry_list_complete,
                       (SELECT COUNT(*) FROM candidate WHERE candidate.motto_show_id = motto_show.id) AS candidate_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id) AS contest_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.assessment IS NOT NULL) AS assessed_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.ranking_position IS NOT NULL) AS ranked_entry_count,
                       (SELECT COUNT(*) FROM contest_entry WHERE contest_entry.motto_show_id = motto_show.id AND contest_entry.contest_participation_id IS NOT NULL) AS assigned_entry_count,
                       (SELECT COUNT(*) FROM contest_participation participation
                        WHERE participation.contest_id = motto_show.contest_id AND participation.active = 1) AS active_participant_count,
                       (SELECT COUNT(*) FROM published_ballot ballot
                        WHERE ballot.motto_show_id = motto_show.id AND ballot.status = 'ABGESTIMMT') AS published_ballot_voted_count,
                       (SELECT COUNT(*) FROM published_ballot ballot
                        WHERE ballot.motto_show_id = motto_show.id AND ballot.status = 'NICHT_ABGESTIMMT') AS published_ballot_not_voted_count,
                       (SELECT COUNT(*) FROM contest_participation participation
                        WHERE participation.contest_id = motto_show.contest_id AND NOT EXISTS (
                          SELECT 1 FROM published_ballot ballot
                          WHERE ballot.motto_show_id = motto_show.id AND ballot.contest_participation_id = participation.id
                        )) AS published_ballot_unrecorded_count,
                       motto_show.ballot_closed_at,
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

    Optional<ShowContext> findContext(long showId) {
        return jdbcTemplate.query("""
                SELECT motto_show.id, motto_show.contest_id, contest.is_current, motto_show.entry_list_complete
                FROM motto_show JOIN contest ON contest.id = motto_show.contest_id
                WHERE motto_show.id = ?
                """, (resultSet, rowNumber) -> new ShowContext(
                resultSet.getLong(1), resultSet.getLong(2), resultSet.getBoolean(3), resultSet.getBoolean(4)
        ), showId).stream().findFirst();
    }

    MottoShow create(long contestId, int showNumber, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO motto_show (contest_id, show_number, name, created_at, updated_at)
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, contestId);
            statement.setInt(2, showNumber);
            statement.setString(3, name);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) throw new IllegalStateException("SQLite did not return an ID for the new motto show.");
        return findById(generatedId.longValue()).orElseThrow();
    }

    boolean showNumberExists(long contestId, int showNumber) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE contest_id = ? AND show_number = ?)", Boolean.class, contestId, showNumber
        ));
    }

    boolean otherShowNumberExists(long contestId, long showId, int showNumber) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE contest_id = ? AND id <> ? AND show_number = ?)", Boolean.class,
                contestId, showId, showNumber
        ));
    }

    boolean updateHistorical(long contestId, long showId, int showNumber, String name) {
        return jdbcTemplate.update("""
                UPDATE motto_show SET show_number = ?, name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND contest_id = ?
                """, showNumber, name, showId, contestId) == 1;
    }

    boolean delete(long contestId, long showId) {
        return jdbcTemplate.update("DELETE FROM motto_show WHERE id = ? AND contest_id = ?", showId, contestId) == 1;
    }

    boolean hasReferences(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM contest_entry WHERE motto_show_id = ?)
                    OR EXISTS(SELECT 1 FROM candidate WHERE motto_show_id = ?)
                    OR EXISTS(SELECT 1 FROM legacy_received_score WHERE motto_show_id = ?)
                    OR EXISTS(SELECT 1 FROM legacy_result WHERE motto_show_id = ?)
                    OR EXISTS(SELECT 1 FROM ballot_snapshot WHERE motto_show_id = ?)
                    OR EXISTS(SELECT 1 FROM published_ballot WHERE motto_show_id = ?)
                """, Boolean.class, showId, showId, showId, showId, showId, showId));
    }

    boolean updateEntryListComplete(long showId, boolean complete) {
        return jdbcTemplate.update("""
                UPDATE motto_show SET entry_list_complete = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, complete, showId) == 1;
    }

    private static MottoShow mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MottoShow(
                resultSet.getLong("id"),
                resultSet.getLong("contest_id"),
                resultSet.getInt("show_number"),
                resultSet.getString("name"),
                resultSet.getBoolean("entry_list_complete"),
                resultSet.getInt("candidate_count"),
                resultSet.getInt("contest_entry_count"),
                resultSet.getInt("assessed_entry_count"),
                resultSet.getInt("ranked_entry_count"),
                resultSet.getInt("assigned_entry_count"),
                resultSet.getInt("active_participant_count"),
                resultSet.getInt("published_ballot_voted_count"),
                resultSet.getInt("published_ballot_not_voted_count"),
                resultSet.getInt("published_ballot_unrecorded_count"),
                nullableInstant(resultSet, "ballot_closed_at"),
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
