package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Used inside the same serialized transaction as each mutation, before any writes. */
@Component
public class ResultClosureGuard {
    private final JdbcTemplate jdbc;
    public ResultClosureGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void showOpen(long showId) {
        check(jdbc.queryForObject("SELECT COUNT(*) FROM motto_show WHERE id = ? AND result_closed_at IS NOT NULL", Integer.class, showId));
    }
    public void contestOpen(long contestId) {
        check(jdbc.queryForObject("SELECT COUNT(*) FROM motto_show WHERE contest_id = ? AND result_closed_at IS NOT NULL", Integer.class, contestId));
    }
    public void entryAssignmentsOpen(List<Long> entryIds) {
        for (long id : entryIds) check(jdbc.queryForObject("""
                SELECT COUNT(*) FROM contest_entry e JOIN motto_show s ON s.id = e.motto_show_id
                WHERE e.id = ? AND e.contest_participation_id IS NOT NULL AND s.result_closed_at IS NOT NULL
                """, Integer.class, id));
    }
    private static void check(Integer count) {
        if (count != null && count > 0) throw new ApiConflictException("SHOW_RESULT_CLOSED",
                "Öffne zuerst das Ergebnis aller betroffenen Shows bewusst wieder. Die Änderung würde eine abgeschlossene Wertung verändern.");
    }
}
