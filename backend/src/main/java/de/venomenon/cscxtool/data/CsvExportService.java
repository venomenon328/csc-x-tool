package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.shared.EntryListReadiness;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CsvExportService {

    private final JdbcTemplate jdbc;
    private final Map<String, String> countryNames;

    public CsvExportService(DataSource dataSource, CountryCatalog countries) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.countryNames = countries.findAll().stream().collect(Collectors.toUnmodifiableMap(Country::code, Country::name));
    }

    public byte[] candidates() {
        return csv(List.of("CSC-Ausgabe", "Show", "Interpret", "Titel", "YouTube-URL", "Kommentar", "Status", "Manuelle Position", "Eigene Einreichung"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,candidate.artist,candidate.title,candidate.youtube_url,
                               candidate.comment,candidate.status,candidate.manual_position,motto_show.selected_candidate_id=candidate.id
                        FROM candidate JOIN motto_show ON motto_show.id=candidate.motto_show_id
                        JOIN contest ON contest.id=motto_show.contest_id
                        ORDER BY contest.display_order,motto_show.show_number,candidate.manual_position
                        """, (r,n) -> List.of(r.getString(1),show(r.getInt(2),r.getString(3)),r.getString(4),r.getString(5),r.getString(6),
                        nullable(r,7),r.getString(8),Integer.toString(r.getInt(9)),yesNo(r.getBoolean(10)))));
    }

    public byte[] contestEntries() {
        return csv(List.of("CSC-Ausgabe", "Show", "Songliste vollständig", "Interpret", "Titel", "Quell- oder YouTube-URL", "Kommentar", "Einschätzung (1–5)", "Sicherheit (1–5)", "Manuelle Position", "Rangposition", "Teilnehmer", "Land"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,motto_show.entry_list_complete,contest_entry.artist,contest_entry.title,contest_entry.youtube_url,
                               contest_entry.comment,contest_entry.assessment,contest_entry.assessment_confidence,contest_entry.pool_position,
                               contest_entry.ranking_position,participant.display_name,participation.country_code
                        FROM contest_entry JOIN motto_show ON motto_show.id=contest_entry.motto_show_id
                        JOIN contest ON contest.id=motto_show.contest_id
                        LEFT JOIN contest_participation participation ON participation.id=contest_entry.contest_participation_id
                        LEFT JOIN participant ON participant.id=participation.participant_id
                        ORDER BY contest.display_order,motto_show.show_number,contest_entry.pool_position
                        """, (r,n) -> List.of(r.getString(1),show(r.getInt(2),r.getString(3)),yesNo(r.getBoolean(4)),r.getString(5),r.getString(6),nullable(r,7),nullable(r,8),
                        nullableNumber(r,9),nullableNumber(r,10),Integer.toString(r.getInt(11)),nullableNumber(r,12),nullable(r,13),nullable(r,14))));
    }

    public byte[] participants() {
        return csv(List.of("CSC-Ausgabe", "Anzeigename", "Ländercode", "Land", "Teilnahme aktiv", "Stammdaten aktiv", "Aliasse"), jdbc.query("""
                SELECT contest.name,participant.id,participant.display_name,participation.country_code,participation.active,participant.active,
                       COALESCE(group_concat(participant_alias.alias, ' | '), '') AS aliases
                FROM contest_participation participation
                JOIN contest ON contest.id=participation.contest_id
                JOIN participant ON participant.id=participation.participant_id
                LEFT JOIN participant_alias ON participant_alias.participant_id=participant.id
                GROUP BY participation.id ORDER BY contest.display_order,participant.display_name COLLATE NOCASE,participant.id
                """, (r,n) -> List.of(r.getString(1),r.getString(3),r.getString(4),countryNames.getOrDefault(r.getString(4),r.getString(4)),
                yesNo(r.getBoolean(5)),yesNo(r.getBoolean(6)),r.getString(7))));
    }

    public byte[] results() {
        List<ResultCsvRow> rows = jdbc.query("""
                SELECT contest.name,motto_show.show_number,motto_show.name,voter.display_name,voter_participation.country_code,
                       own_entry.artist,own_entry.title,voter_participation.id=contest.own_participation_id,
                       ballot.status,position.rank,motto_show.entry_list_complete,contest.is_current,
                       EXISTS(SELECT 1 FROM contest_entry entry WHERE entry.motto_show_id=motto_show.id),
                       NOT EXISTS(SELECT 1 FROM contest_entry entry WHERE entry.motto_show_id=motto_show.id
                                  AND entry.contest_participation_id IS NULL)
                FROM contest
                JOIN motto_show ON motto_show.contest_id=contest.id
                JOIN contest_entry own_entry ON own_entry.motto_show_id=motto_show.id
                  AND own_entry.contest_participation_id=contest.own_participation_id
                JOIN contest_participation voter_participation ON voter_participation.contest_id=contest.id
                JOIN participant voter ON voter.id=voter_participation.participant_id
                LEFT JOIN published_ballot ballot ON ballot.motto_show_id=motto_show.id
                  AND ballot.contest_participation_id=voter_participation.id
                LEFT JOIN published_ballot_position position ON position.published_ballot_id=ballot.id
                  AND position.contest_entry_id=own_entry.id
                WHERE contest.own_participation_id IS NOT NULL
                ORDER BY contest.display_order,motto_show.show_number,voter.display_name COLLATE NOCASE,voter.id
                """, (r,n) -> {
            boolean own = r.getBoolean(8);
            String status = r.getString(9);
            int rank = r.getInt(10);
            boolean ranked = !r.wasNull();
            String state = own ? "EIGENE_EINREICHUNG" : ranked ? "RANG_1_BIS_15"
                    : "ABGESTIMMT".equals(status) ? "AUSSERHALB_TOP_15"
                    : "NICHT_ABGESTIMMT".equals(status) ? "NICHT_ABGESTIMMT" : "UNERFASST";
            String points = ranked ? Integer.toString(CscPoints.pointsForRank(rank)) : "AUSSERHALB_TOP_15".equals(state) ? "0" : "";
            boolean ready = EntryListReadiness.isReady(r.getBoolean(11), r.getBoolean(12), r.getBoolean(13), r.getBoolean(14));
            return new ResultCsvRow(ready, List.of(r.getString(1), show(r.getInt(2), r.getString(3)), r.getString(4), r.getString(5),
                    r.getString(6) + " – " + r.getString(7), state, ranked ? Integer.toString(rank) : "", points));
        });
        return csv(List.of("CSC-Ausgabe", "Show", "Abstimmender", "Land", "Eigene Einreichung", "Zustand", "Rang", "Abgeleitete Punkte"),
                rows.stream().filter(ResultCsvRow::ready).map(ResultCsvRow::cells).toList());
    }

    /** Separate archive export; these values are never used to manufacture a published ballot. */
    public byte[] legacyResults() {
        return csv(List.of("CSC-Ausgabe", "Show", "Teilnehmer", "Land", "Legacy-Status", "Legacy-Punkte", "Offizielle Gesamtpunkte", "Endplatzierung", "Geteilt", "Ergebnis abgeschlossen am", "Archiviert am"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,participant.display_name,participation.country_code,
                               score.status,score.points,legacy.official_total_points,legacy.final_place,legacy.final_place_tied,
                               legacy.results_closed_at,COALESCE(score.archived_at,legacy.archived_at)
                        FROM motto_show JOIN contest ON contest.id=motto_show.contest_id
                        LEFT JOIN legacy_received_score score ON score.motto_show_id=motto_show.id
                        LEFT JOIN contest_participation participation ON participation.id=score.contest_participation_id
                        LEFT JOIN participant ON participant.id=participation.participant_id
                        LEFT JOIN legacy_result legacy ON legacy.motto_show_id=motto_show.id
                        WHERE score.id IS NOT NULL OR legacy.id IS NOT NULL
                        ORDER BY contest.display_order,motto_show.show_number,participant.display_name COLLATE NOCASE,participant.id
                        """, (r,n) -> List.of(r.getString(1), show(r.getInt(2), r.getString(3)), nullable(r,4), nullable(r,5), nullable(r,6),
                        nullableNumber(r,7), nullableNumber(r,8), nullableNumber(r,9), yesNo(r.getBoolean(10)), nullable(r,11), nullable(r,12))));
    }

    /** Long format keeps an absent ballot distinct from a ranked song and never invents ranks below the Top 15. */
    public byte[] publishedBallots() {
        return csv(List.of("CSC-Ausgabe", "Show", "Abstimmender", "Land", "Stimmzettelstatus", "Bewertungszustand", "Rang", "Abgeleitete Punkte", "Interpret", "Titel", "Quell- oder YouTube-URL", "Einreichender", "Land Einreichender"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,voter.display_name,voter_participation.country_code,
                               COALESCE(ballot.status, 'UNERFASST'),position.rank,entry.artist,entry.title,entry.youtube_url,
                               submitter.display_name,submitter_participation.country_code
                        FROM motto_show
                        JOIN contest ON contest.id=motto_show.contest_id
                        JOIN contest_participation voter_participation ON voter_participation.contest_id=contest.id
                        JOIN participant voter ON voter.id=voter_participation.participant_id
                        LEFT JOIN published_ballot ballot ON ballot.motto_show_id=motto_show.id
                          AND ballot.contest_participation_id=voter_participation.id
                        LEFT JOIN published_ballot_position position ON position.published_ballot_id=ballot.id
                        LEFT JOIN contest_entry entry ON entry.id=position.contest_entry_id
                        LEFT JOIN contest_participation submitter_participation ON submitter_participation.id=entry.contest_participation_id
                        LEFT JOIN participant submitter ON submitter.id=submitter_participation.participant_id
                        ORDER BY contest.display_order,motto_show.show_number,voter.display_name COLLATE NOCASE,voter.id,position.rank
                        """, (r,n) -> {
                    int rank = r.getInt(7);
                    boolean ranked = !r.wasNull();
                    String ballotStatus = r.getString(6);
                    String state = ranked ? "RANKED" : "NICHT_ABGESTIMMT".equals(ballotStatus) ? "NO_BALLOT" : "UNKNOWN";
                    return List.of(r.getString(1),show(r.getInt(2),r.getString(3)),r.getString(4),r.getString(5),ballotStatus,state,
                            ranked ? Integer.toString(rank) : "", ranked ? Integer.toString(CscPoints.pointsForRank(rank)) : "",
                            nullable(r,8),nullable(r,9),nullable(r,10),nullable(r,11),nullable(r,12));
                }));
    }

    private static byte[] csv(List<String> header, List<List<String>> rows) {
        StringBuilder value = new StringBuilder("\uFEFF");
        append(value, header);
        for (List<String> row : rows) append(value, row);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void append(StringBuilder destination, List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            if (i > 0) destination.append(';');
            String value = row.get(i) == null ? "" : row.get(i);
            boolean quoted = value.indexOf(';') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
            if (quoted) destination.append('"');
            destination.append(value.replace("\"", "\"\""));
            if (quoted) destination.append('"');
        }
        destination.append("\r\n");
    }
    private static String show(int number, String name) { return number + " – " + name; }
    private static String yesNo(boolean value) { return value ? "Ja" : "Nein"; }
    private static String nullable(java.sql.ResultSet result, int index) throws java.sql.SQLException { return result.getString(index) == null ? "" : result.getString(index); }
    private static String nullableNumber(java.sql.ResultSet result, int index) throws java.sql.SQLException { int value = result.getInt(index); return result.wasNull() ? "" : Integer.toString(value); }
    private record ResultCsvRow(boolean ready, List<String> cells) { }
}
