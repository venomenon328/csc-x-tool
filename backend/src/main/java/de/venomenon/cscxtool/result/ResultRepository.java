package de.venomenon.cscxtool.result;

import de.venomenon.cscxtool.shared.EntryListReadiness;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ResultRepository {
    private final JdbcTemplate jdbc;

    ResultRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Optional<ShowFacts> findShow(long showId) {
        return jdbc.query("""
                SELECT show.id, show.contest_id, contest.own_participation_id, contest.is_current,
                       show.entry_list_complete,
                       EXISTS(SELECT 1 FROM contest_entry WHERE motto_show_id = show.id),
                       NOT EXISTS(SELECT 1 FROM contest_entry WHERE motto_show_id = show.id AND contest_participation_id IS NULL)
                FROM motto_show show JOIN contest ON contest.id = show.contest_id WHERE show.id = ?
                """, (r, n) -> new ShowFacts(r.getLong(1), r.getLong(2), nullableLong(r, 3), r.getBoolean(4),
                r.getBoolean(5), r.getBoolean(6), r.getBoolean(7)), showId).stream().findFirst();
    }

    Optional<OwnParticipation> findOwnParticipation(long contestId, long participationId) {
        return jdbc.query("""
                SELECT participation.id, participant.id, participant.display_name, participation.country_code
                FROM contest_participation participation JOIN participant ON participant.id = participation.participant_id
                WHERE participation.id = ? AND participation.contest_id = ?
                """, (r, n) -> new OwnParticipation(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4)), participationId, contestId)
                .stream().findFirst();
    }

    Optional<OwnEntry> findOwnEntry(long showId, long participationId) {
        return jdbc.query("""
                SELECT id, artist, title, youtube_url FROM contest_entry
                WHERE motto_show_id = ? AND contest_participation_id = ?
                """, (r, n) -> new OwnEntry(r.getLong(1), r.getString(2), r.getString(3), r.getString(4)), showId, participationId)
                .stream().findFirst();
    }

    boolean selectedCandidateDiffers(long showId, OwnEntry ownEntry) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT selected_candidate_id IS NOT NULL AND NOT EXISTS(
                  SELECT 1 FROM candidate
                  WHERE candidate.id = motto_show.selected_candidate_id
                    AND lower(trim(candidate.artist)) = lower(trim(?))
                    AND lower(trim(candidate.title)) = lower(trim(?))
                ) FROM motto_show WHERE id = ?
                """, Boolean.class, ownEntry.artist(), ownEntry.title(), showId));
    }

    List<DerivedLine> findDerivedLines(long showId, long ownParticipationId, long ownEntryId) {
        return jdbc.query("""
                SELECT participation.id, participant.id, participant.display_name, participation.country_code, ballot.status, position.rank
                FROM motto_show show
                JOIN contest_participation participation ON participation.contest_id = show.contest_id
                JOIN participant ON participant.id = participation.participant_id
                LEFT JOIN published_ballot ballot ON ballot.motto_show_id = show.id AND ballot.contest_participation_id = participation.id
                LEFT JOIN published_ballot_position position ON position.published_ballot_id = ballot.id AND position.contest_entry_id = ?
                WHERE show.id = ?
                ORDER BY CASE WHEN participation.id = ? THEN 0 ELSE 1 END, participant.display_name COLLATE NOCASE, participant.id
                """, (r, n) -> new DerivedLine(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), nullableInt(r, 6)),
                ownEntryId, showId, ownParticipationId);
    }

    LegacyResult findLegacy(long showId) {
        LegacyDetails details = jdbc.query("""
                SELECT results_closed_at, final_place, final_place_tied, official_total_points, archived_at
                FROM legacy_result WHERE motto_show_id = ?
                """, (r, n) -> new LegacyDetails(r.getString(1), nullableInt(r, 2), r.getBoolean(3), nullableInt(r, 4), r.getString(5)), showId)
                .stream().findFirst().orElse(null);
        List<LegacyScore> scores = jdbc.query("""
                SELECT score.id, participation.id, participant.id, participant.display_name, participation.country_code,
                       score.status, score.points, score.created_at, score.updated_at, score.archived_at
                FROM legacy_received_score score
                JOIN contest_participation participation ON participation.id = score.contest_participation_id
                JOIN participant ON participant.id = participation.participant_id
                WHERE score.motto_show_id = ? ORDER BY participant.display_name COLLATE NOCASE, participant.id
                """, (r, n) -> new LegacyScore(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5),
                r.getString(6), nullableInt(r, 7), r.getString(8), r.getString(9), r.getString(10)), showId);
        return new LegacyResult(details, scores);
    }

    void deleteLegacy(long showId) {
        jdbc.update("DELETE FROM legacy_received_score WHERE motto_show_id = ?", showId);
        jdbc.update("DELETE FROM legacy_result WHERE motto_show_id = ?", showId);
    }

    record ShowFacts(
            long showId, long contestId, Long ownParticipationId, boolean currentContest, boolean entryListComplete,
            boolean hasEntries, boolean allEntriesAssigned
    ) {
        boolean entryListReady() {
            return EntryListReadiness.isReady(entryListComplete, currentContest, hasEntries, allEntriesAssigned);
        }
    }
    record OwnParticipation(long id, long participantId, String displayName, String countryCode) { }
    record OwnEntry(long id, String artist, String title, String youtubeUrl) { }
    record DerivedLine(long participationId, long participantId, String displayName, String countryCode, String ballotStatus, Integer rank) { }
    record LegacyResult(LegacyDetails details, List<LegacyScore> scores) { }
    record LegacyDetails(String resultsClosedAt, Integer finalPlace, boolean finalPlaceTied, Integer officialTotalPoints, String archivedAt) { }
    record LegacyScore(long id, long participationId, long participantId, String displayName, String countryCode, String status,
                       Integer points, String createdAt, String updatedAt, String archivedAt) { }

    private static Long nullableLong(ResultSet r, int column) throws SQLException {
        long value = r.getLong(column); return r.wasNull() ? null : value;
    }
    private static Integer nullableInt(ResultSet r, int column) throws SQLException {
        int value = r.getInt(column); return r.wasNull() ? null : value;
    }
}
