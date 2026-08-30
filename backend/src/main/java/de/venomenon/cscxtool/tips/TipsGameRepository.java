package de.venomenon.cscxtool.tips;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class TipsGameRepository {

    private final JdbcTemplate jdbc;

    TipsGameRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<TipsShowFacts> findShowFacts(long showId) {
        return jdbc.query("""
                SELECT show.id, show.contest_id, contest.is_current,
                       (SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = show.id),
                       (SELECT COUNT(*) FROM contest_entry WHERE motto_show_id = show.id AND contest_participation_id IS NULL),
                       (SELECT COUNT(*) FROM contest_participation WHERE contest_id = show.contest_id)
                FROM motto_show show
                JOIN contest ON contest.id = show.contest_id
                WHERE show.id = ?
                """, (result, row) -> new TipsShowFacts(result.getLong(1), result.getLong(2), result.getBoolean(3),
                result.getInt(4), result.getInt(5), result.getInt(6)), showId).stream().findFirst();
    }

    Optional<TipsGame> findGame(long showId) {
        return jdbc.query("""
                SELECT id,motto_show_id,contest_id,status,created_at,updated_at,resolved_at
                FROM tips_game WHERE motto_show_id = ?
                """, TipsGameRepository::mapGame, showId).stream().findFirst();
    }

    TipsGame createGame(long showId, long contestId) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO tips_game (motto_show_id,contest_id,status,created_at,updated_at,resolved_at)
                    VALUES (?, ?, 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setLong(2, contestId);
            return statement;
        }, keys);
        Number key = keys.getKey();
        if (key == null) throw new IllegalStateException("SQLite did not return an ID for the tips game.");
        return findGame(showId).orElseThrow();
    }

    void replaceAssignments(long gameId, List<TipsAssignmentCommand> assignments) {
        jdbc.update("DELETE FROM tips_game_assignment WHERE tips_game_id = ?", gameId);
        jdbc.batchUpdate("""
                INSERT INTO tips_game_assignment (tips_game_id,contest_entry_id,guessed_participation_id,confidence,note)
                VALUES (?, ?, ?, ?, ?)
                """, assignments.stream().map(assignment -> new Object[] {
                gameId, assignment.entryId(), assignment.guessedParticipationId(),
                assignment.confidence() == null ? null : assignment.confidence().name(), assignment.note()
        }).toList());
        jdbc.update("UPDATE tips_game SET updated_at = CURRENT_TIMESTAMP WHERE id = ?", gameId);
    }

    void resolve(long gameId) {
        jdbc.update("""
                UPDATE tips_game
                SET status = 'RESOLVED', resolved_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, gameId);
    }

    void reopen(long gameId) {
        jdbc.update("""
                UPDATE tips_game
                SET status = 'DRAFT', resolved_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, gameId);
    }

    List<TipsParticipant> findParticipants(long contestId) {
        return jdbc.query("""
                SELECT participation.id,participant.id,participant.display_name,participation.country_code,
                       participation.active,participant.active
                FROM contest_participation participation
                JOIN participant ON participant.id = participation.participant_id
                WHERE participation.contest_id = ?
                ORDER BY participation.active DESC,participant.display_name COLLATE NOCASE,participant.id
                """, (result, row) -> new TipsParticipant(result.getLong(1), result.getLong(2), result.getString(3),
                result.getString(4), result.getBoolean(5), result.getBoolean(6)), contestId);
    }

    boolean participationBelongsToShow(long showId, long participationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM motto_show show
                  JOIN contest_participation participation ON participation.contest_id = show.contest_id
                  WHERE show.id = ? AND participation.id = ?
                )
                """, Boolean.class, showId, participationId));
    }

    List<TipsEntry> findEntries(long showId, Long gameId) {
        long safeGameId = gameId == null ? -1L : gameId;
        return jdbc.query("""
                SELECT entry.id,entry.artist,entry.title,entry.youtube_url,
                       actual_participation.id,actual_participant.id,actual_participant.display_name,actual_participation.country_code,
                       assignment.id,assignment.guessed_participation_id,assignment.confidence,assignment.note
                FROM contest_entry entry
                LEFT JOIN contest_participation actual_participation ON actual_participation.id = entry.contest_participation_id
                LEFT JOIN participant actual_participant ON actual_participant.id = actual_participation.participant_id
                LEFT JOIN tips_game_assignment assignment ON assignment.contest_entry_id = entry.id AND assignment.tips_game_id = ?
                WHERE entry.motto_show_id = ?
                ORDER BY entry.pool_position
                """, (result, row) -> new TipsEntry(
                result.getLong(1), result.getString(2), result.getString(3), result.getString(4),
                nullableLong(result, 5), nullableLong(result, 6), result.getString(7), result.getString(8),
                nullableLong(result, 9), nullableLong(result, 10), nullableEnum(result.getString(11)), result.getString(12)
        ), safeGameId, showId);
    }

    List<TipsSubmissionHistoryItem> findSubmissionHistory(long showId, long participationId) {
        return jdbc.query("""
                SELECT entry.id,show.id,show.show_number,show.name,contest.id,contest.name,contest.is_current,
                       author_participation.country_code,entry.artist,entry.title,entry.youtube_url
                FROM motto_show focus_show
                JOIN contest_participation focus ON focus.contest_id = focus_show.contest_id
                JOIN contest_participation author_participation ON author_participation.participant_id = focus.participant_id
                JOIN contest_entry entry ON entry.contest_participation_id = author_participation.id
                JOIN motto_show show ON show.id = entry.motto_show_id
                JOIN contest ON contest.id = show.contest_id
                WHERE focus_show.id = ? AND focus.id = ?
                  AND entry.motto_show_id <> focus_show.id
                  AND (
                    contest.is_current = 0
                    OR (
                      contest.id = focus_show.contest_id
                      AND show.show_number < focus_show.show_number
                      AND EXISTS (SELECT 1 FROM contest_entry ready_entry WHERE ready_entry.motto_show_id = show.id)
                      AND NOT EXISTS (
                        SELECT 1 FROM contest_entry unassigned_entry
                        WHERE unassigned_entry.motto_show_id = show.id
                          AND unassigned_entry.contest_participation_id IS NULL
                      )
                    )
                  )
                ORDER BY contest.display_order DESC,show.show_number DESC,entry.pool_position
                """, (result, row) -> new TipsSubmissionHistoryItem(
                result.getLong(1), result.getLong(2), result.getInt(3), result.getString(4), result.getLong(5), result.getString(6),
                result.getBoolean(7), result.getString(8), result.getString(9), result.getString(10), result.getString(11)
        ), showId, participationId);
    }

    private static TipsGame mapGame(ResultSet result, int row) throws SQLException {
        return new TipsGame(result.getLong(1), result.getLong(2), result.getLong(3), TipsGameStatus.valueOf(result.getString(4)),
                result.getTimestamp(5).toInstant(), result.getTimestamp(6).toInstant(), nullableInstant(result, 7));
    }

    private static Long nullableLong(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static Instant nullableInstant(ResultSet result, int column) throws SQLException {
        java.sql.Timestamp value = result.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static TipsConfidence nullableEnum(String value) {
        return value == null ? null : TipsConfidence.valueOf(value);
    }
}

record TipsShowFacts(long showId, long contestId, boolean currentContest, int entryCount, int unassignedEntryCount, int participationCount) { }
record TipsGame(long id, long showId, long contestId, TipsGameStatus status, Instant createdAt, Instant updatedAt, Instant resolvedAt) { }
record TipsAssignmentCommand(long entryId, long guessedParticipationId, TipsConfidence confidence, String note) { }
record TipsParticipant(long participationId, long participantId, String displayName, String countryCode, boolean active, boolean identityActive) { }
record TipsEntry(long id, String artist, String title, String youtubeUrl, Long actualParticipationId, Long actualParticipantId,
                 String actualDisplayName, String actualCountryCode, Long tipId, Long guessedParticipationId,
                 TipsConfidence confidence, String note) { }
record TipsSubmissionHistoryItem(long entryId, long showId, int showNumber, String showName, long contestId, String contestName,
                                 boolean currentContest, String countryCode, String artist, String title, String youtubeUrl) { }
