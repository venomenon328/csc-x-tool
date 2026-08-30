package de.venomenon.cscxtool.publishedballot;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
class PublishedBallotRepository {

    private final JdbcTemplate jdbc;
    private final Map<String, String> countryNames;

    PublishedBallotRepository(JdbcTemplate jdbc, CountryCatalog countries) {
        this.jdbc = jdbc;
        Map<String, String> names = new HashMap<>();
        for (Country country : countries.findAll()) names.put(country.code(), country.name());
        this.countryNames = Map.copyOf(names);
    }

    Optional<ShowFacts> findShowFacts(long showId) {
        return jdbc.query("""
                SELECT show.id, show.contest_id, contest.is_current, show.entry_list_complete,
                       show.ballot_closed_at IS NOT NULL
                FROM motto_show show JOIN contest ON contest.id = show.contest_id
                WHERE show.id = ?
                """, (r, n) -> new ShowFacts(r.getLong(1), r.getLong(2), r.getBoolean(3), r.getBoolean(4), r.getBoolean(5)), showId)
                .stream().findFirst();
    }

    List<PublishedBallotParticipant> findParticipants(long showId) {
        return jdbc.query("""
                SELECT participation.id, participant.id, participant.display_name, participation.country_code,
                       COALESCE(group_concat(alias.alias, char(31)), '')
                FROM motto_show show
                JOIN contest_participation participation ON participation.contest_id = show.contest_id
                JOIN participant ON participant.id = participation.participant_id
                LEFT JOIN participant_alias alias ON alias.participant_id = participant.id
                WHERE show.id = ?
                GROUP BY participation.id
                ORDER BY participant.display_name COLLATE NOCASE, participant.id
                """, (r, n) -> new PublishedBallotParticipant(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4),
                countryNames.getOrDefault(r.getString(4), r.getString(4)), splitAliases(r.getString(5))), showId);
    }

    List<PublishedBallotEntry> findEntries(long showId) {
        return jdbc.query("""
                SELECT entry.id, entry.motto_show_id, entry.artist, entry.title, entry.youtube_url,
                       participation.id, participant.id, participant.display_name, participation.country_code
                FROM contest_entry entry
                LEFT JOIN contest_participation participation ON participation.id = entry.contest_participation_id
                LEFT JOIN participant ON participant.id = participation.participant_id
                WHERE entry.motto_show_id = ?
                ORDER BY entry.pool_position, entry.id
                """, (r, n) -> new PublishedBallotEntry(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5),
                nullableLong(r, 6), nullableLong(r, 7), r.getString(8), r.getString(9)), showId);
    }

    List<PublishedBallot> findBallots(long showId) {
        return jdbc.query("""
                SELECT id, motto_show_id, contest_id, contest_participation_id, status, created_at, updated_at
                FROM published_ballot WHERE motto_show_id = ?
                """, PublishedBallotRepository::mapBallot, showId);
    }

    Optional<PublishedBallot> findBallot(long showId, long participationId) {
        return jdbc.query("""
                SELECT id, motto_show_id, contest_id, contest_participation_id, status, created_at, updated_at
                FROM published_ballot WHERE motto_show_id = ? AND contest_participation_id = ?
                """, PublishedBallotRepository::mapBallot, showId, participationId).stream().findFirst();
    }

    List<PublishedBallotPosition> findPositions(long ballotId) {
        return jdbc.query("""
                SELECT published_ballot_id, contest_entry_id, rank
                FROM published_ballot_position WHERE published_ballot_id = ? ORDER BY rank
                """, (r, n) -> new PublishedBallotPosition(r.getLong(1), r.getLong(2), r.getInt(3)), ballotId);
    }

    long createBallot(long showId, long contestId, long participationId, PublishedBallotStatus status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO published_ballot (motto_show_id, contest_id, contest_participation_id, status, created_at, updated_at)
                    VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, showId);
            statement.setLong(2, contestId);
            statement.setLong(3, participationId);
            statement.setString(4, status.name());
            return statement;
        }, keyHolder);
        Number id = keyHolder.getKey();
        if (id == null) throw new IllegalStateException("SQLite did not return an ID for the published ballot.");
        return id.longValue();
    }

    void updateStatus(long ballotId, PublishedBallotStatus status) {
        jdbc.update("UPDATE published_ballot SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", status.name(), ballotId);
    }

    void deletePositions(long ballotId) {
        jdbc.update("DELETE FROM published_ballot_position WHERE published_ballot_id = ?", ballotId);
    }

    void insertPositions(long ballotId, List<PublishedBallotPositionRequest> positions) {
        for (PublishedBallotPositionRequest position : positions) {
            jdbc.update("INSERT INTO published_ballot_position (published_ballot_id, contest_entry_id, rank) VALUES (?, ?, ?)",
                    ballotId, position.entryId(), position.rank());
        }
    }

    void deleteBallot(long ballotId) { jdbc.update("DELETE FROM published_ballot WHERE id = ?", ballotId); }

    boolean hasBallotPositionsForEntry(long entryId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM published_ballot_position WHERE contest_entry_id = ?)", Boolean.class, entryId));
    }

    boolean hasPublishedBallots(long showId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM published_ballot WHERE motto_show_id = ?)", Boolean.class, showId));
    }

    boolean assignmentWouldMakeOwnEntry(long entryId, long participationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(
                  SELECT 1 FROM published_ballot_position position
                  JOIN published_ballot ballot ON ballot.id = position.published_ballot_id
                  WHERE position.contest_entry_id = ? AND ballot.contest_participation_id = ?
                )
                """, Boolean.class, entryId, participationId));
    }

    private static PublishedBallot mapBallot(ResultSet r, int n) throws java.sql.SQLException {
        return new PublishedBallot(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4),
                PublishedBallotStatus.valueOf(r.getString(5)), r.getTimestamp(6).toInstant(), r.getTimestamp(7).toInstant());
    }

    private static Long nullableLong(ResultSet r, int index) throws java.sql.SQLException {
        long value = r.getLong(index);
        return r.wasNull() ? null : value;
    }
    private static List<String> splitAliases(String value) {
        return value == null || value.isBlank() ? List.of() : List.of(value.split(String.valueOf((char) 31)));
    }

    record ShowFacts(long showId, long contestId, boolean currentContest, boolean entryListComplete, boolean ownBallotClosed) {
        boolean entryListReady() { return entryListComplete || (currentContest && ownBallotClosed); }
    }
}
