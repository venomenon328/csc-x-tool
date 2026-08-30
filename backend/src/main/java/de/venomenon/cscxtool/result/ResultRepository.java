package de.venomenon.cscxtool.result;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ResultRepository {

    private final JdbcTemplate jdbcTemplate;

    ResultRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    boolean showExists(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM motto_show WHERE id = ?)", Boolean.class, showId
        ));
    }

    ResultState findState(long showId) {
        return jdbcTemplate.queryForObject("""
                SELECT ballot_closed_at, results_closed_at, selected_candidate_id,
                       official_total_points, final_place, final_place_tied
                FROM motto_show WHERE id = ?
                """, (resultSet, rowNumber) -> new ResultState(
                nullableInstant(resultSet, "ballot_closed_at"),
                nullableInstant(resultSet, "results_closed_at"),
                nullableLong(resultSet, "selected_candidate_id"),
                nullableInt(resultSet, "official_total_points"),
                nullableInt(resultSet, "final_place"),
                resultSet.getBoolean("final_place_tied")
        ), showId);
    }

    Optional<ResultSubmissionResponse> findSelectedCandidate(long showId) {
        return jdbcTemplate.query("""
                SELECT candidate.id, candidate.artist, candidate.title, candidate.youtube_url
                FROM motto_show
                JOIN candidate ON candidate.id = motto_show.selected_candidate_id
                WHERE motto_show.id = ?
                """, (resultSet, rowNumber) -> new ResultSubmissionResponse(
                resultSet.getLong("id"), resultSet.getString("artist"), resultSet.getString("title"), resultSet.getString("youtube_url")
        ), showId).stream().findFirst();
    }

    List<ReceivedScoreLine> findLines(long showId) {
        return jdbcTemplate.query("""
                SELECT participant.id AS participant_id, participant.display_name, participation.country_code, participation.active,
                       received_score.id AS received_score_id, received_score.status, received_score.points
                FROM motto_show show
                JOIN contest_participation participation ON participation.contest_id = show.contest_id
                JOIN participant ON participant.id = participation.participant_id
                LEFT JOIN received_score
                  ON received_score.contest_participation_id = participation.id AND received_score.motto_show_id = show.id
                WHERE show.id = ? AND (participation.active = 1 OR received_score.id IS NOT NULL)
                ORDER BY participation.active DESC, participant.display_name COLLATE NOCASE, participant.id
                """, (resultSet, rowNumber) -> new ReceivedScoreLine(
                resultSet.getLong("participant_id"),
                resultSet.getString("display_name"),
                resultSet.getString("country_code"),
                resultSet.getBoolean("active"),
                statusOrUnknown(resultSet),
                nullableInt(resultSet, "points"),
                resultSet.getObject("received_score_id") != null
        ), showId);
    }

    boolean participantExists(long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM participant WHERE id = ?)", Boolean.class, participantId
        ));
    }

    boolean mayReceiveScore(long showId, long participationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM contest_participation participation
                  JOIN motto_show show ON show.contest_id = participation.contest_id
                  WHERE show.id = ? AND participation.id = ? AND (participation.active = 1 OR EXISTS(
                    SELECT 1 FROM received_score WHERE motto_show_id = ? AND contest_participation_id = participation.id
                  ))
                )
                """, Boolean.class, showId, participationId, showId));
    }

    void saveScore(long showId, long participationId, ReceivedScoreStatus status, Integer points) {
        jdbcTemplate.update("""
                INSERT INTO received_score (
                  motto_show_id, contest_id, contest_participation_id, status, points, created_at, updated_at
                ) VALUES (?, (SELECT contest_id FROM motto_show WHERE id = ?), ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (motto_show_id, contest_participation_id) DO UPDATE SET
                  status = excluded.status,
                  points = excluded.points,
                  updated_at = CURRENT_TIMESTAMP
                """, showId, showId, participationId, status.name(), points);
    }

    void updateDetails(long showId, Integer officialTotalPoints, Integer finalPlace, boolean finalPlaceTied) {
        int changed = jdbcTemplate.update("""
                UPDATE motto_show
                SET official_total_points = ?, final_place = ?, final_place_tied = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, officialTotalPoints, finalPlace, finalPlaceTied, showId);
        if (changed != 1) {
            throw new IllegalStateException("The validated motto show disappeared while updating result details.");
        }
    }

    int calculatedTotalPoints(long showId) {
        Integer total = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(points), 0) FROM received_score
                WHERE motto_show_id = ? AND status = 'ABGESTIMMT'
                """, Integer.class, showId);
        return total == null ? 0 : total;
    }

    boolean hasUnknownActiveParticipant(long showId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM contest_participation participation
                  JOIN motto_show show ON show.contest_id = participation.contest_id
                  LEFT JOIN received_score
                    ON received_score.contest_participation_id = participation.id AND received_score.motto_show_id = show.id
                  WHERE show.id = ? AND participation.active = 1
                    AND (received_score.id IS NULL OR received_score.status = 'UNBEKANNT')
                )
                """, Boolean.class, showId));
    }

    void close(long showId) {
        int changed = jdbcTemplate.update("""
                UPDATE motto_show SET results_closed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, showId);
        if (changed != 1) {
            throw new IllegalStateException("The validated motto show disappeared while closing results.");
        }
    }

    void reopen(long showId) {
        int changed = jdbcTemplate.update("""
                UPDATE motto_show SET results_closed_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, showId);
        if (changed != 1) {
            throw new IllegalStateException("The validated motto show disappeared while reopening results.");
        }
    }

    private static ReceivedScoreStatus statusOrUnknown(ResultSet resultSet) throws SQLException {
        String value = resultSet.getString("status");
        return value == null ? ReceivedScoreStatus.UNBEKANNT : ReceivedScoreStatus.valueOf(value);
    }

    private static Instant nullableInstant(ResultSet resultSet, String name) throws SQLException {
        java.sql.Timestamp value = resultSet.getTimestamp(name);
        return value == null ? null : value.toInstant();
    }

    private static Long nullableLong(ResultSet resultSet, String name) throws SQLException {
        long value = resultSet.getLong(name);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet resultSet, String name) throws SQLException {
        int value = resultSet.getInt(name);
        return resultSet.wasNull() ? null : value;
    }

    record ResultState(
            Instant ballotClosedAt,
            Instant resultsClosedAt,
            Long selectedCandidateId,
            Integer officialTotalPoints,
            Integer finalPlace,
            boolean finalPlaceTied
    ) {
    }
}
