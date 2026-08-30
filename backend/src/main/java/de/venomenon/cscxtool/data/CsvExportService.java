package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
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
        return csv(List.of("CSC-Ausgabe", "Show", "Interpret", "Titel", "YouTube-URL", "Kommentar", "Einschätzung (1–5)", "Sicherheit (1–5)", "Manuelle Position", "Rangposition", "Teilnehmer", "Land"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,contest_entry.artist,contest_entry.title,contest_entry.youtube_url,
                               contest_entry.comment,contest_entry.assessment,contest_entry.assessment_confidence,contest_entry.pool_position,
                               contest_entry.ranking_position,participant.display_name,participation.country_code
                        FROM contest_entry JOIN motto_show ON motto_show.id=contest_entry.motto_show_id
                        JOIN contest ON contest.id=motto_show.contest_id
                        LEFT JOIN contest_participation participation ON participation.id=contest_entry.contest_participation_id
                        LEFT JOIN participant ON participant.id=participation.participant_id
                        ORDER BY contest.display_order,motto_show.show_number,contest_entry.pool_position
                        """, (r,n) -> List.of(r.getString(1),show(r.getInt(2),r.getString(3)),r.getString(4),r.getString(5),r.getString(6),nullable(r,7),
                        nullableNumber(r,8),nullableNumber(r,9),Integer.toString(r.getInt(10)),nullableNumber(r,11),nullable(r,12),nullable(r,13))));
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
        return csv(List.of("CSC-Ausgabe", "Show", "Teilnehmer", "Abstimmungsstatus", "Punkte", "Berechnete Gesamtpunkte", "Offizielle Gesamtpunkte", "Endplatzierung", "Platzierung geteilt"),
                jdbc.query("""
                        SELECT contest.name,motto_show.show_number,motto_show.name,participant.display_name,
                          COALESCE(received_score.status, 'UNBEKANNT'),received_score.points,
                          COALESCE((SELECT SUM(points) FROM received_score scores WHERE scores.motto_show_id=motto_show.id AND scores.status='ABGESTIMMT'),0),
                          motto_show.official_total_points,motto_show.final_place,motto_show.final_place_tied
                        FROM motto_show JOIN contest ON contest.id=motto_show.contest_id
                        JOIN contest_participation participation ON participation.contest_id=motto_show.contest_id
                        JOIN participant ON participant.id=participation.participant_id
                        LEFT JOIN received_score ON received_score.motto_show_id=motto_show.id
                          AND received_score.contest_participation_id=participation.id
                        WHERE participation.active=1 OR received_score.id IS NOT NULL
                        ORDER BY contest.display_order,motto_show.show_number,participant.display_name COLLATE NOCASE,participant.id
                        """, (r,n) -> List.of(r.getString(1),show(r.getInt(2),r.getString(3)),r.getString(4),r.getString(5),nullableNumber(r,6),
                        Integer.toString(r.getInt(7)),nullableNumber(r,8),nullableNumber(r,9),yesNo(r.getBoolean(10)))));
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
}
