package de.venomenon.cscxtool.contest;

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
public class ContestRepository {

    private final JdbcTemplate jdbcTemplate;

    public ContestRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Contest> findAll() {
        return jdbcTemplate.query(selectSql("ORDER BY contest.display_order"), ContestRepository::mapContest);
    }

    public Optional<Contest> findById(long contestId) {
        return jdbcTemplate.query(selectSql("WHERE contest.id = ?"), ContestRepository::mapContest, contestId).stream().findFirst();
    }

    public Optional<Contest> findCurrent() {
        return jdbcTemplate.query(selectSql("WHERE contest.is_current = 1"), ContestRepository::mapContest).stream().findFirst();
    }

    public Contest create(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contest (name, display_order, is_current, created_at, updated_at)
                    VALUES (?, (SELECT COALESCE(MAX(display_order), 0) + 1 FROM contest), 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new contest.");
        }
        return findById(generatedId.longValue()).orElseThrow();
    }

    public boolean rename(long contestId, String name) {
        return jdbcTemplate.update("""
                UPDATE contest SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """, name, contestId) == 1;
    }

    public boolean exists(long contestId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM contest WHERE id = ?)", Boolean.class, contestId
        ));
    }

    public boolean nameExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM contest WHERE name = ? COLLATE NOCASE)", Boolean.class, name
        ));
    }

    public boolean otherContestHasName(long contestId, String name) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM contest WHERE id <> ? AND name = ? COLLATE NOCASE)", Boolean.class, contestId, name
        ));
    }

    public void makeCurrent(long contestId) {
        jdbcTemplate.update("UPDATE contest SET is_current = 0, updated_at = CURRENT_TIMESTAMP WHERE is_current = 1");
        int changed = jdbcTemplate.update("UPDATE contest SET is_current = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", contestId);
        if (changed != 1) {
            throw new IllegalStateException("The validated contest disappeared while changing the current contest.");
        }
    }

    public List<ContestParticipation> findParticipations(long contestId) {
        return jdbcTemplate.query("""
                SELECT id, contest_id, participant_id, country_code, active, created_at, updated_at
                FROM contest_participation WHERE contest_id = ? ORDER BY participant_id
                """, ContestRepository::mapParticipation, contestId);
    }

    public Optional<ContestParticipation> findParticipation(long contestId, long participantId) {
        return jdbcTemplate.query("""
                SELECT id, contest_id, participant_id, country_code, active, created_at, updated_at
                FROM contest_participation WHERE contest_id = ? AND participant_id = ?
                """, ContestRepository::mapParticipation, contestId, participantId).stream().findFirst();
    }

    public Optional<ContestParticipation> findParticipationForShow(long showId, long participantId) {
        return jdbcTemplate.query("""
                SELECT participation.id, participation.contest_id, participation.participant_id, participation.country_code,
                       participation.active, participation.created_at, participation.updated_at
                FROM contest_participation participation
                JOIN motto_show show ON show.contest_id = participation.contest_id
                WHERE show.id = ? AND participation.participant_id = ?
                """, ContestRepository::mapParticipation, showId, participantId).stream().findFirst();
    }

    public ContestParticipation createParticipation(long contestId, long participantId, String countryCode, boolean active) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO contest_participation (
                      contest_id, participant_id, country_code, active, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, contestId);
            statement.setLong(2, participantId);
            statement.setString(3, countryCode);
            statement.setBoolean(4, active);
            return statement;
        }, keyHolder);
        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return an ID for the new contest participation.");
        }
        return findParticipation(contestId, participantId).orElseThrow();
    }

    public boolean updateParticipation(long contestId, long participantId, String countryCode, boolean active) {
        return jdbcTemplate.update("""
                UPDATE contest_participation
                SET country_code = ?, active = ?, updated_at = CURRENT_TIMESTAMP
                WHERE contest_id = ? AND participant_id = ?
                """, countryCode, active, contestId, participantId) == 1;
    }

    public boolean deleteParticipation(long contestId, long participantId) {
        return jdbcTemplate.update("DELETE FROM contest_participation WHERE contest_id = ? AND participant_id = ?", contestId, participantId) == 1;
    }

    public boolean updateOwnParticipation(long contestId, Long participationId) {
        return jdbcTemplate.update("UPDATE contest SET own_participation_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", participationId, contestId) == 1;
    }

    public boolean hasDerivedOwnResults(long contestId, long participationId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                  SELECT 1
                  FROM motto_show show
                  JOIN contest_entry own_entry
                    ON own_entry.motto_show_id = show.id AND own_entry.contest_participation_id = ?
                  WHERE show.contest_id = ?
                    AND (show.entry_list_complete = 1 OR (
                      (SELECT is_current FROM contest WHERE id = show.contest_id) = 1
                      AND show.ballot_closed_at IS NOT NULL
                      AND EXISTS (SELECT 1 FROM contest_entry WHERE motto_show_id = show.id)
                      AND NOT EXISTS (SELECT 1 FROM contest_entry WHERE motto_show_id = show.id AND contest_participation_id IS NULL)
                    ))
                )
                """, Boolean.class, participationId, contestId));
    }

    public boolean participationIsReferenced(long contestId, long participantId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM contest_participation participation
                  WHERE participation.contest_id = ? AND participation.participant_id = ?
                    AND (EXISTS (SELECT 1 FROM contest_entry entry WHERE entry.contest_participation_id = participation.id)
                      OR EXISTS (SELECT 1 FROM legacy_received_score score WHERE score.contest_participation_id = participation.id)
                      OR EXISTS (SELECT 1 FROM published_ballot ballot WHERE ballot.contest_participation_id = participation.id)
                      OR EXISTS (SELECT 1 FROM contest WHERE own_participation_id = participation.id))
                )
                """, Boolean.class, contestId, participantId));
    }

    private static String selectSql(String suffix) {
        return """
                SELECT contest.id, contest.name, contest.display_order, contest.is_current,
                       (SELECT COUNT(*) FROM contest_participation WHERE contest_id = contest.id) AS participant_count,
                       (SELECT COUNT(*) FROM motto_show WHERE contest_id = contest.id) AS show_count,
                       contest.own_participation_id,
                       contest.created_at, contest.updated_at
                FROM contest
                """ + suffix;
    }

    private static Contest mapContest(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Contest(
                resultSet.getLong("id"), resultSet.getString("name"), resultSet.getInt("display_order"),
                resultSet.getBoolean("is_current"), resultSet.getInt("participant_count"), resultSet.getInt("show_count"),
                nullableLong(resultSet, "own_participation_id"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static ContestParticipation mapParticipation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ContestParticipation(
                resultSet.getLong("id"), resultSet.getLong("contest_id"), resultSet.getLong("participant_id"),
                resultSet.getString("country_code"), resultSet.getBoolean("active"),
                resultSet.getTimestamp("created_at").toInstant(), resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }
}
