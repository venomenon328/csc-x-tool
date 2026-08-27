package de.venomenon.cscxtool.data;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExportService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final BackupService backups;

    public ExportService(DataSource dataSource, ObjectMapper objectMapper, BackupService backups) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.backups = backups;
    }

    public byte[] exportJson() {
        try {
            return objectMapper.writeValueAsBytes(snapshot());
        } catch (RuntimeException exception) {
            throw new BackupStorageException("Der JSON-Export konnte nicht erstellt werden.", exception);
        }
    }

    public ExportFormat.FullExport snapshot() {
        return new ExportFormat.FullExport(
                ExportFormat.FORMAT, ExportFormat.VERSION, Instant.now().toString(), applicationVersion(),
                SchemaSupport.schemaVersion(jdbcTemplate.getDataSource()), new ExportFormat.Data(
                        mottoShows(), candidates(), participants(), participantAliases(), contestEntries(),
                        ballotSnapshots(), ballotSnapshotItems(), receivedScores()
                )
        );
    }

    public ExportFormat.FullExport readAndValidate(Path input) {
        try {
            ExportFormat.FullExport export = objectMapper.rebuild()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build().readValue(input, ExportFormat.FullExport.class);
            if (!ExportFormat.FORMAT.equals(export.format()) || export.formatVersion() != ExportFormat.VERSION) {
                throw new BackupFileException("EXPORT_FORMAT_UNSUPPORTED", "Das JSON-Format wird von dieser Anwendung nicht unterst\u00fctzt.");
            }
            if (export.schemaVersion() > SchemaSupport.CURRENT_SCHEMA_VERSION) {
                throw new BackupFileException("EXPORT_SCHEMA_TOO_NEW", "Der JSON-Export stammt aus einer neueren Datenbankschemaversion.");
            }
            if (export.data() == null || export.exportedAt() == null) {
                throw new BackupFileException("EXPORT_INVALID", "Der JSON-Export enth\u00e4lt keine vollst\u00e4ndigen fachlichen Daten.");
            }
            requireLists(export.data());
            return export;
        } catch (BackupFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupFileException("EXPORT_INVALID", "Die JSON-Datei ist nicht lesbar oder entspricht nicht dem Exportformat.", exception);
        }
    }

    public void restoreInto(Path databaseFile, ExportFormat.FullExport export) {
        try {
            SchemaSupport.migrate(databaseFile);
            JdbcTemplate stage = new JdbcTemplate(de.venomenon.cscxtool.system.SqliteDataSourceFactory.create(databaseFile));
            ExportFormat.Data data = export.data();
            // Liquibase creates the immutable initial-show seeds. JSON restore is deliberately a full
            // replacement, so remove those stage-only rows before inserting the exported IDs.
            stage.update("DELETE FROM received_score");
            stage.update("DELETE FROM ballot_snapshot_item");
            stage.update("DELETE FROM ballot_snapshot");
            stage.update("DELETE FROM contest_entry");
            stage.update("UPDATE motto_show SET selected_candidate_id = NULL");
            stage.update("DELETE FROM candidate");
            stage.update("DELETE FROM participant_alias");
            stage.update("DELETE FROM participant");
            stage.update("DELETE FROM motto_show");
            for (ExportFormat.MottoShow row : data.mottoShows()) {
                stage.update("""
                        INSERT INTO motto_show (id, show_number, name, selected_candidate_id, ballot_closed_at, results_closed_at,
                          final_place, final_place_tied, official_total_points, created_at, updated_at)
                        VALUES (?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.showNumber(), row.name(), row.ballotClosedAt(), row.resultsClosedAt(),
                        row.finalPlace(), row.finalPlaceTied(), row.officialTotalPoints(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.Participant row : data.participants()) {
                stage.update("INSERT INTO participant (id, display_name, country_code, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)",
                        row.id(), row.displayName(), row.countryCode(), row.active(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.ParticipantAlias row : data.participantAliases()) {
                stage.update("INSERT INTO participant_alias (id, participant_id, alias) VALUES (?, ?, ?)", row.id(), row.participantId(), row.alias());
            }
            for (ExportFormat.Candidate row : data.candidates()) {
                stage.update("""
                        INSERT INTO candidate (id, motto_show_id, artist, title, youtube_url, comment, status, manual_position, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.artist(), row.title(), row.youtubeUrl(), row.comment(), row.status(),
                        row.manualPosition(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.ContestEntry row : data.contestEntries()) {
                stage.update("""
                        INSERT INTO contest_entry (id, motto_show_id, artist, title, youtube_url, comment, listened, relisten,
                          ranking_position, participant_id, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.artist(), row.title(), row.youtubeUrl(), row.comment(), row.listened(),
                        row.relisten(), row.rankingPosition(), row.participantId(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.BallotSnapshot row : data.ballotSnapshots()) {
                stage.update("INSERT INTO ballot_snapshot (id, motto_show_id, snapshot_number, created_at, is_current) VALUES (?, ?, ?, ?, ?)",
                        row.id(), row.mottoShowId(), row.snapshotNumber(), row.createdAt(), row.current());
            }
            for (ExportFormat.BallotSnapshotItem row : data.ballotSnapshotItems()) {
                stage.update("""
                        INSERT INTO ballot_snapshot_item (id, ballot_snapshot_id, rank, contest_entry_id, artist_snapshot, title_snapshot, youtube_url_snapshot)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.ballotSnapshotId(), row.rank(), row.contestEntryId(), row.artistSnapshot(), row.titleSnapshot(), row.youtubeUrlSnapshot());
            }
            for (ExportFormat.ReceivedScore row : data.receivedScores()) {
                stage.update("""
                        INSERT INTO received_score (id, motto_show_id, participant_id, status, points, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.participantId(), row.status(), row.points(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.MottoShow row : data.mottoShows()) {
                stage.update("UPDATE motto_show SET selected_candidate_id = ? WHERE id = ?", row.selectedCandidateId(), row.id());
            }
            SchemaSupport.verify(databaseFile, SchemaSupport.CURRENT_SCHEMA_VERSION);
        } catch (BackupFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupFileException("EXPORT_INVALID", "Die fachlichen Daten des JSON-Exports sind nicht wiederherstellbar.", exception);
        }
    }

    private List<ExportFormat.MottoShow> mottoShows() {
        return jdbcTemplate.query("""
                SELECT id, show_number, name, selected_candidate_id, ballot_closed_at, results_closed_at, final_place,
                       final_place_tied, official_total_points, created_at, updated_at FROM motto_show ORDER BY id
                """, (r, n) -> new ExportFormat.MottoShow(r.getLong(1), r.getInt(2), r.getString(3), nullableLong(r, 4),
                r.getString(5), r.getString(6), nullableInt(r, 7), r.getBoolean(8), nullableInt(r, 9), r.getString(10), r.getString(11)));
    }
    private List<ExportFormat.Candidate> candidates() {
        return jdbcTemplate.query("SELECT id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at FROM candidate ORDER BY id",
                (r,n) -> new ExportFormat.Candidate(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getInt(8),r.getString(9),r.getString(10)));
    }
    private List<ExportFormat.Participant> participants() {
        return jdbcTemplate.query("SELECT id,display_name,country_code,active,created_at,updated_at FROM participant ORDER BY id",
                (r,n) -> new ExportFormat.Participant(r.getLong(1),r.getString(2),r.getString(3),r.getBoolean(4),r.getString(5),r.getString(6)));
    }
    private List<ExportFormat.ParticipantAlias> participantAliases() {
        return jdbcTemplate.query("SELECT id,participant_id,alias FROM participant_alias ORDER BY id", (r,n) -> new ExportFormat.ParticipantAlias(r.getLong(1),r.getLong(2),r.getString(3)));
    }
    private List<ExportFormat.ContestEntry> contestEntries() {
        return jdbcTemplate.query("SELECT id,motto_show_id,artist,title,youtube_url,comment,listened,relisten,ranking_position,participant_id,created_at,updated_at FROM contest_entry ORDER BY id",
                (r,n) -> new ExportFormat.ContestEntry(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getBoolean(7),r.getBoolean(8),nullableInt(r,9),nullableLong(r,10),r.getString(11),r.getString(12)));
    }
    private List<ExportFormat.BallotSnapshot> ballotSnapshots() {
        return jdbcTemplate.query("SELECT id,motto_show_id,snapshot_number,created_at,is_current FROM ballot_snapshot ORDER BY id", (r,n) -> new ExportFormat.BallotSnapshot(r.getLong(1),r.getLong(2),r.getInt(3),r.getString(4),r.getBoolean(5)));
    }
    private List<ExportFormat.BallotSnapshotItem> ballotSnapshotItems() {
        return jdbcTemplate.query("SELECT id,ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot FROM ballot_snapshot_item ORDER BY id", (r,n) -> new ExportFormat.BallotSnapshotItem(r.getLong(1),r.getLong(2),r.getInt(3),nullableLong(r,4),r.getString(5),r.getString(6),r.getString(7)));
    }
    private List<ExportFormat.ReceivedScore> receivedScores() {
        return jdbcTemplate.query("SELECT id,motto_show_id,participant_id,status,points,created_at,updated_at FROM received_score ORDER BY id", (r,n) -> new ExportFormat.ReceivedScore(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),nullableInt(r,5),r.getString(6),r.getString(7)));
    }
    private static Long nullableLong(java.sql.ResultSet result, int index) throws java.sql.SQLException { long value=result.getLong(index); return result.wasNull()?null:value; }
    private static Integer nullableInt(java.sql.ResultSet result, int index) throws java.sql.SQLException { int value=result.getInt(index); return result.wasNull()?null:value; }
    private static void requireLists(ExportFormat.Data data) {
        if (data.mottoShows()==null || data.candidates()==null || data.participants()==null || data.participantAliases()==null
                || data.contestEntries()==null || data.ballotSnapshots()==null || data.ballotSnapshotItems()==null || data.receivedScores()==null) {
            throw new BackupFileException("EXPORT_INVALID", "Der JSON-Export enth\u00e4lt keine vollst\u00e4ndigen fachlichen Daten.");
        }
    }
    private String applicationVersion() { return backups.applicationVersion(); }
}
