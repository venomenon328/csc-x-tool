package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.contest.ContestNotFoundException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ContestResultRepository {
    private final JdbcTemplate jdbc;
    public ContestResultRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long contestForShow(long showId) {
        return jdbc.query("SELECT contest_id FROM motto_show WHERE id = ?", (r, n) -> r.getLong(1), showId)
                .stream().findFirst().orElseThrow(() -> new ShowNotFoundException(showId));
    }

    /** Caller owns one real read transaction across all queries. */
    public ResultData load(long contestId) {
        boolean current = jdbc.query("SELECT is_current FROM contest WHERE id = ?", (r, n) -> r.getBoolean(1), contestId)
                .stream().findFirst().orElseThrow(() -> new ContestNotFoundException(contestId));
        var shows = jdbc.query("SELECT id, show_number, name, entry_list_complete, result_closed_at FROM motto_show WHERE contest_id = ? ORDER BY show_number",
                (r, n) -> new ResultData.Show(r.getLong(1), r.getInt(2), r.getString(3), r.getBoolean(4), r.getString(5)), contestId);
        var participants = jdbc.query("""
                SELECT cp.id, p.id, p.display_name, cp.country_code FROM contest_participation cp
                JOIN participant p ON p.id = cp.participant_id WHERE cp.contest_id = ? ORDER BY p.display_name COLLATE NOCASE, cp.id
                """, (r, n) -> new ResultData.Participation(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4)), contestId);
        var entries = jdbc.query("""
                SELECT e.id, e.motto_show_id, e.contest_participation_id, e.artist, e.title FROM contest_entry e
                JOIN motto_show s ON s.id = e.motto_show_id WHERE s.contest_id = ? ORDER BY e.pool_position, e.id
                """, (r, n) -> new ResultData.Entry(r.getLong(1), r.getLong(2), nullableLong(r, 3), r.getString(4), r.getString(5)), contestId);
        var ballots = jdbc.query("""
                SELECT b.id, b.motto_show_id, b.contest_participation_id, b.status FROM published_ballot b
                JOIN motto_show s ON s.id = b.motto_show_id WHERE s.contest_id = ?
                """, (r, n) -> new ResultData.Ballot(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4)), contestId);
        var positions = jdbc.query("""
                SELECT p.published_ballot_id, p.contest_entry_id, p.rank FROM published_ballot_position p
                JOIN published_ballot b ON b.id = p.published_ballot_id JOIN motto_show s ON s.id = b.motto_show_id WHERE s.contest_id = ?
                """, (r, n) -> new ResultData.Position(r.getLong(1), r.getLong(2), r.getInt(3)), contestId);
        return new ResultData(contestId, current, shows, participants, entries, ballots, positions);
    }

    void setClosed(long showId, String closedAt) {
        jdbc.update("UPDATE motto_show SET result_closed_at = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", closedAt, showId);
    }

    private static Long nullableLong(ResultSet result, int column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }
}
