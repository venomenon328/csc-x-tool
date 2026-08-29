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
        return csv(List.of("Show", "Interpret", "Titel", "YouTube-URL", "Kommentar", "Status", "Manuelle Position", "Eigene Einreichung"),
                jdbc.query("""
                        SELECT motto_show.show_number, motto_show.name, candidate.artist, candidate.title, candidate.youtube_url,
                               candidate.comment, candidate.status, candidate.manual_position, motto_show.selected_candidate_id = candidate.id
                        FROM candidate JOIN motto_show ON motto_show.id = candidate.motto_show_id
                        ORDER BY motto_show.show_number, candidate.manual_position
                        """, (r,n) -> List.of(show(r.getInt(1),r.getString(2)), r.getString(3),r.getString(4),r.getString(5),
                        nullable(r,6),r.getString(7),Integer.toString(r.getInt(8)), yesNo(r.getBoolean(9)))));
    }

    public byte[] contestEntries() {
        return csv(List.of("Show", "Interpret", "Titel", "YouTube-URL", "Kommentar", "Gehört", "Wiedervorlage", "Manuelle Position", "Rangposition", "Teilnehmer"),
                jdbc.query("""
                        SELECT motto_show.show_number,motto_show.name,contest_entry.artist,contest_entry.title,contest_entry.youtube_url,
                               contest_entry.comment,contest_entry.listened,contest_entry.relisten,contest_entry.pool_position,
                               contest_entry.ranking_position,participant.display_name
                        FROM contest_entry JOIN motto_show ON motto_show.id=contest_entry.motto_show_id
                        LEFT JOIN participant ON participant.id=contest_entry.participant_id
                        ORDER BY motto_show.show_number,contest_entry.pool_position
                        """, (r,n) -> List.of(show(r.getInt(1),r.getString(2)),r.getString(3),r.getString(4),r.getString(5),nullable(r,6),
                        yesNo(r.getBoolean(7)),yesNo(r.getBoolean(8)),Integer.toString(r.getInt(9)),nullableNumber(r,10),nullable(r,11))));
    }

    public byte[] participants() {
        return csv(List.of("Anzeigename", "Ländercode", "Land", "Aktiv", "Aliasse"), jdbc.query("""
                SELECT participant.id,participant.display_name,participant.country_code,participant.active,
                       COALESCE(group_concat(participant_alias.alias, ' | '), '') AS aliases
                FROM participant LEFT JOIN participant_alias ON participant_alias.participant_id=participant.id
                GROUP BY participant.id ORDER BY participant.display_name COLLATE NOCASE,participant.id
                """, (r,n) -> List.of(r.getString(2),r.getString(3),countryNames.getOrDefault(r.getString(3),r.getString(3)),
                yesNo(r.getBoolean(4)),r.getString(5))));
    }

    public byte[] results() {
        return csv(List.of("Show", "Teilnehmer", "Abstimmungsstatus", "Punkte", "Berechnete Gesamtpunkte", "Offizielle Gesamtpunkte", "Endplatzierung", "Platzierung geteilt"),
                jdbc.query("""
                        SELECT motto_show.show_number,motto_show.name,participant.display_name,
                          COALESCE(received_score.status, 'UNBEKANNT'),received_score.points,
                          COALESCE((SELECT SUM(points) FROM received_score scores WHERE scores.motto_show_id=motto_show.id AND scores.status='ABGESTIMMT'),0),
                          motto_show.official_total_points,motto_show.final_place,motto_show.final_place_tied
                        FROM motto_show CROSS JOIN participant
                        LEFT JOIN received_score ON received_score.motto_show_id=motto_show.id
                          AND received_score.participant_id=participant.id
                        WHERE participant.active=1 OR received_score.id IS NOT NULL
                        ORDER BY motto_show.show_number,participant.display_name COLLATE NOCASE,participant.id
                        """, (r,n) -> List.of(show(r.getInt(1),r.getString(2)),r.getString(3),r.getString(4),nullableNumber(r,5),
                        Integer.toString(r.getInt(6)),nullableNumber(r,7),nullableNumber(r,8),yesNo(r.getBoolean(9)))));
    }

    private static byte[] csv(List<String> header, List<List<String>> rows) {
        StringBuilder value = new StringBuilder("\uFEFF");
        append(value, header);
        for (List<String> row : rows) append(value, row);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void append(StringBuilder destination, List<String> row) {
        for (int i=0;i<row.size();i++) {
            if (i>0) destination.append(';');
            String value = row.get(i) == null ? "" : row.get(i);
            boolean quoted = value.indexOf(';')>=0 || value.indexOf('"')>=0 || value.indexOf('\r')>=0 || value.indexOf('\n')>=0;
            if (quoted) destination.append('"');
            destination.append(value.replace("\"", "\"\""));
            if (quoted) destination.append('"');
        }
        destination.append("\r\n");
    }
    private static String show(int number, String name) { return number + " – " + name; }
    private static String yesNo(boolean value) { return value ? "Ja" : "Nein"; }
    private static String nullable(java.sql.ResultSet result, int index) throws java.sql.SQLException { return result.getString(index) == null ? "" : result.getString(index); }
    private static String nullableNumber(java.sql.ResultSet result, int index) throws java.sql.SQLException { int value=result.getInt(index); return result.wasNull()?"":Integer.toString(value); }
}
