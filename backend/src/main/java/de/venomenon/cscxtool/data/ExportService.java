package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.candidate.CandidateStatus;
import de.venomenon.cscxtool.participant.Country;
import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.result.ReceivedScoreStatus;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.system.SqliteDataSourceFactory;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Service
public class ExportService {

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final BackupService backups;
    private final Set<String> validCountryCodes;
    private final Runnable snapshotEstablished;

    @Autowired
    public ExportService(DataSource dataSource, ObjectMapper objectMapper, BackupService backups, CountryCatalog countries) {
        this(dataSource, objectMapper, backups, countries, () -> { });
    }

    ExportService(DataSource dataSource, ObjectMapper objectMapper, BackupService backups) {
        this(dataSource, objectMapper, backups, new CountryCatalog(objectMapper), () -> { });
    }

    ExportService(
            DataSource dataSource, ObjectMapper objectMapper, BackupService backups,
            CountryCatalog countries, Runnable snapshotEstablished
    ) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.backups = backups;
        this.validCountryCodes = countries.findAll().stream().map(Country::code).collect(Collectors.toUnmodifiableSet());
        this.snapshotEstablished = snapshotEstablished;
    }

    public byte[] exportJson() {
        try {
            return objectMapper.writeValueAsBytes(snapshot());
        } catch (BackupStorageException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BackupStorageException("Der JSON-Export konnte nicht erstellt werden.", exception);
        }
    }

    /**
     * Reads every exported table through one SQLite read transaction. The transaction fixes the
     * WAL snapshot at the first query and the held JDBC connection keeps the restore write lock
     * from switching the live database during the export.
     */
    public ExportFormat.FullExport snapshot() {
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                List<ExportFormat.MottoShow> shows = mottoShows(connection);
                snapshotEstablished.run();
                return new ExportFormat.FullExport(
                        ExportFormat.FORMAT, ExportFormat.VERSION, Instant.now().toString(), applicationVersion(),
                        schemaVersion(connection), new ExportFormat.Data(
                                shows, candidates(connection), participants(connection), participantAliases(connection),
                                contestEntries(connection), ballotSnapshots(connection), ballotSnapshotItems(connection),
                                receivedScores(connection)
                        )
                );
            } finally {
                try {
                    connection.rollback();
                } finally {
                    connection.setAutoCommit(autoCommit);
                }
            }
        } catch (SQLException exception) {
            throw new BackupStorageException("Der JSON-Export konnte nicht aus der SQLite-Datenbank gelesen werden.", exception);
        }
    }

    public ExportFormat.FullExport readAndValidate(Path input) {
        try {
            ObjectMapper strictMapper = objectMapper.rebuild()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
            Map<?, ?> header = strictMapper.readValue(input, Map.class);
            Object versionValue = header.get("formatVersion");
            if (!(versionValue instanceof Number versionNumber)) {
                throw invalid("Der JSON-Export enth\u00e4lt keine unterst\u00fctzte Formatversion.");
            }
            ExportFormat.FullExport export = switch (versionNumber.intValue()) {
                case ExportFormat.LEGACY_VERSION -> upgradeV1(strictMapper.readValue(input, ExportFormat.FullExportV1.class));
                case ExportFormat.VERSION_2 -> upgradeV2(strictMapper.readValue(input, ExportFormat.FullExportV2.class));
                case ExportFormat.VERSION -> strictMapper.readValue(input, ExportFormat.FullExport.class);
                default -> throw invalid("Das JSON-Format wird von dieser Anwendung nicht unterst\u00fctzt.");
            };
            validateExport(export);
            return export;
        } catch (BackupFileException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BackupFileException("EXPORT_INVALID", "Die JSON-Datei ist nicht lesbar oder entspricht nicht dem Exportformat.", exception);
        }
    }

    /** Restores only a prevalidated export into a disposable SQLite staging database. */
    public void restoreInto(Path databaseFile, ExportFormat.FullExport export) {
        validateExport(export);
        try {
            SchemaSupport.migrate(databaseFile);
        } catch (Exception exception) {
            throw new BackupStorageException("Die Staging-Datenbank f\u00fcr den JSON-Import konnte nicht migriert werden.", exception);
        }

        JdbcTemplate stage = new JdbcTemplate(SqliteDataSourceFactory.create(databaseFile));
        ExportFormat.Data data = export.data();
        try {
            // Liquibase creates the initial-show seeds. JSON restore is deliberately a full
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
                        INSERT INTO contest_entry (id, motto_show_id,artist,title,youtube_url,comment,assessment,assessment_confidence,
                          pool_position,ranking_position,participant_id,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.artist(), row.title(), row.youtubeUrl(), row.comment(), row.assessment(),
                        row.assessmentConfidence(), row.poolPosition(), row.rankingPosition(), row.participantId(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.BallotSnapshot row : data.ballotSnapshots()) {
                stage.update("INSERT INTO ballot_snapshot (id, motto_show_id, snapshot_number, created_at, is_current) VALUES (?, ?, ?, ?, ?)",
                        row.id(), row.mottoShowId(), row.snapshotNumber(), row.createdAt(), row.current());
            }
            for (ExportFormat.BallotSnapshotItem row : data.ballotSnapshotItems()) {
                stage.update("""
                        INSERT INTO ballot_snapshot_item (id, ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.ballotSnapshotId(), row.rank(), row.contestEntryId(), row.artistSnapshot(), row.titleSnapshot(), row.youtubeUrlSnapshot());
            }
            for (ExportFormat.ReceivedScore row : data.receivedScores()) {
                stage.update("""
                        INSERT INTO received_score (id,motto_show_id,participant_id,status,points,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.participantId(), row.status(), row.points(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.MottoShow row : data.mottoShows()) {
                stage.update("UPDATE motto_show SET selected_candidate_id = ? WHERE id = ?", row.selectedCandidateId(), row.id());
            }
        } catch (DataAccessException exception) {
            throw new BackupStorageException("Die vorgepr\u00fcften JSON-Daten konnten nicht in die Staging-Datenbank geschrieben werden.", exception);
        }
        try {
            SchemaSupport.verify(databaseFile, SchemaSupport.CURRENT_SCHEMA_VERSION);
        } catch (BackupFileException exception) {
            throw new BackupStorageException("Die vorbereitete JSON-Wiederherstellung besteht die SQLite-Pr\u00fcfung nicht.", exception);
        }
    }

    private void validateExport(ExportFormat.FullExport export) {
        if (export == null || !ExportFormat.FORMAT.equals(export.format()) || export.formatVersion() != ExportFormat.VERSION) {
            throw invalid("Das JSON-Format wird von dieser Anwendung nicht unterst\u00fctzt.");
        }
        if (export.schemaVersion() < 1) throw invalid("Der JSON-Export enth\u00e4lt keine g\u00fcltige Schemaversion.");
        if (export.schemaVersion() > SchemaSupport.CURRENT_SCHEMA_VERSION) {
            throw new BackupFileException("EXPORT_SCHEMA_TOO_NEW", "Der JSON-Export stammt aus einer neueren Datenbankschemaversion.");
        }
        requireUtcInstant(export.exportedAt(), "Der JSON-Export enth\u00e4lt keinen g\u00fcltigen UTC-Exportzeitpunkt.");
        requireText(export.applicationVersion(), "Der JSON-Export enth\u00e4lt keine Anwendungsversionsangabe.");
        if (export.data() == null) throw invalid("Der JSON-Export enth\u00e4lt keine vollst\u00e4ndigen fachlichen Daten.");
        requireLists(export.data());
        validateData(export.data());
    }

    private void validateData(ExportFormat.Data data) {
        if (data.mottoShows().size() != 12) throw invalid("Ein vollst\u00e4ndiger JSON-Export muss genau zw\u00f6lf Mottoshows enthalten.");
        Set<Long> showIds = uniquePositive(data.mottoShows(), ExportFormat.MottoShow::id, "Mottoshow");
        Set<Integer> showNumbers = new HashSet<>();
        for (ExportFormat.MottoShow show : data.mottoShows()) {
            requireText(show.name(), "Eine Mottoshow ohne Namen ist nicht g\u00fcltig.");
            requireText(show.createdAt(), "Eine Mottoshow ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            requireText(show.updatedAt(), "Eine Mottoshow ohne Aktualisierungszeitpunkt ist nicht g\u00fcltig.");
            if (!showNumbers.add(show.showNumber()) || show.showNumber() < 1 || show.showNumber() > 12) {
                throw invalid("Die Mottoshows m\u00fcssen die eindeutigen Nummern 1 bis 12 enthalten.");
            }
            if (show.finalPlaceTied() && show.finalPlace() == null) throw invalid("Eine geteilte Endplatzierung ben\u00f6tigt eine Endplatzierung.");
            if (show.resultsClosedAt() != null && show.ballotClosedAt() == null) {
                throw invalid("Abgeschlossene Ergebnisse ben\u00f6tigen eine abgeschlossene Abstimmung.");
            }
        }
        if (showNumbers.size() != 12) throw invalid("Die Mottoshows m\u00fcssen die Nummern 1 bis 12 vollst\u00e4ndig abdecken.");

        Set<Long> participantIds = uniquePositive(data.participants(), ExportFormat.Participant::id, "Teilnehmer");
        Map<Long, ExportFormat.Participant> participantsById = new HashMap<>();
        for (ExportFormat.Participant participant : data.participants()) {
            requireText(participant.displayName(), "Ein Teilnehmer ohne Anzeigename ist nicht g\u00fcltig.");
            requireText(participant.createdAt(), "Ein Teilnehmer ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            requireText(participant.updatedAt(), "Ein Teilnehmer ohne Aktualisierungszeitpunkt ist nicht g\u00fcltig.");
            if (!validCountryCodes.contains(participant.countryCode())) throw invalid("Der JSON-Export enth\u00e4lt einen nicht unterst\u00fctzten L\u00e4ndercode.");
            participantsById.put(participant.id(), participant);
        }
        uniquePositive(data.participantAliases(), ExportFormat.ParticipantAlias::id, "Teilnehmeralias");
        for (ExportFormat.ParticipantAlias alias : data.participantAliases()) {
            requireReference(participantIds, alias.participantId(), "Ein Teilnehmeralias verweist auf einen unbekannten Teilnehmer.");
            requireText(alias.alias(), "Ein leerer Teilnehmeralias ist nicht g\u00fcltig.");
        }

        uniquePositive(data.candidates(), ExportFormat.Candidate::id, "Kandidat");
        Map<Long, ExportFormat.Candidate> candidatesById = new HashMap<>();
        Set<String> candidatePositions = new HashSet<>();
        for (ExportFormat.Candidate candidate : data.candidates()) {
            requireReference(showIds, candidate.mottoShowId(), "Ein Kandidat verweist auf eine unbekannte Mottoshow.");
            requireText(candidate.artist(), "Ein Kandidat ohne Interpret ist nicht g\u00fcltig.");
            requireText(candidate.title(), "Ein Kandidat ohne Titel ist nicht g\u00fcltig.");
            requireText(candidate.youtubeUrl(), "Ein Kandidat ohne YouTube-URL ist nicht g\u00fcltig.");
            requireText(candidate.createdAt(), "Ein Kandidat ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            requireText(candidate.updatedAt(), "Ein Kandidat ohne Aktualisierungszeitpunkt ist nicht g\u00fcltig.");
            if (!enumValue(CandidateStatus.class, candidate.status()) || candidate.manualPosition() < 1
                    || !candidatePositions.add(candidate.mottoShowId() + ":" + candidate.manualPosition())) {
                throw invalid("Die Kandidatenreihenfolge oder der Kandidatenstatus ist nicht g\u00fcltig.");
            }
            candidatesById.put(candidate.id(), candidate);
        }
        for (ExportFormat.MottoShow show : data.mottoShows()) {
            if (show.selectedCandidateId() != null) {
                ExportFormat.Candidate candidate = candidatesById.get(show.selectedCandidateId());
                if (candidate == null || candidate.mottoShowId() != show.id()) {
                    throw invalid("Die ausgew\u00e4hlte Einreichung muss zu ihrer Mottoshow geh\u00f6ren.");
                }
            }
        }

        Set<Long> entryIds = uniquePositive(data.contestEntries(), ExportFormat.ContestEntry::id, "Wettbewerbsbeitrag");
        Set<String> entryPositions = new HashSet<>();
        Set<String> poolPositions = new HashSet<>();
        Map<Long, List<Integer>> poolPositionsByShow = new HashMap<>();
        for (ExportFormat.ContestEntry entry : data.contestEntries()) {
            requireReference(showIds, entry.mottoShowId(), "Ein Wettbewerbsbeitrag verweist auf eine unbekannte Mottoshow.");
            if (entry.participantId() != null) requireReference(participantIds, entry.participantId(), "Ein Wettbewerbsbeitrag verweist auf einen unbekannten Teilnehmer.");
            requireText(entry.artist(), "Ein Wettbewerbsbeitrag ohne Interpret ist nicht g\u00fcltig.");
            requireText(entry.title(), "Ein Wettbewerbsbeitrag ohne Titel ist nicht g\u00fcltig.");
            requireText(entry.youtubeUrl(), "Ein Wettbewerbsbeitrag ohne YouTube-URL ist nicht g\u00fcltig.");
            requireText(entry.createdAt(), "Ein Wettbewerbsbeitrag ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            requireText(entry.updatedAt(), "Ein Wettbewerbsbeitrag ohne Aktualisierungszeitpunkt ist nicht g\u00fcltig.");
            if (!validAssessmentPair(entry.assessment(), entry.assessmentConfidence())) {
                throw invalid("Einsch\u00e4tzung und Sicherheit eines Wettbewerbsbeitrags m\u00fcssen gemeinsam leer sein oder jeweils zwischen 1 und 5 liegen.");
            }
            if (entry.poolPosition() < 1 || !poolPositions.add(entry.mottoShowId() + ":" + entry.poolPosition())) {
                throw invalid("Die manuelle Position eines Wettbewerbsbeitrags ist nicht g\u00fcltig.");
            }
            poolPositionsByShow.computeIfAbsent(entry.mottoShowId(), ignored -> new ArrayList<>()).add(entry.poolPosition());
            if (entry.rankingPosition() != null && (entry.rankingPosition() < 1
                    || !entryPositions.add(entry.mottoShowId() + ":" + entry.rankingPosition()))) {
                throw invalid("Die Rangposition eines Wettbewerbsbeitrags ist nicht g\u00fcltig.");
            }
        }
        for (List<Integer> positions : poolPositionsByShow.values()) {
            positions.sort(Comparator.naturalOrder());
            for (int index = 0; index < positions.size(); index++) {
                if (positions.get(index) != index + 1) {
                    throw invalid("Die manuelle Reihenfolge der Wettbewerbsbeitr\u00e4ge muss l\u00fcckenlos sein.");
                }
            }
        }

        Set<Long> snapshotIds = uniquePositive(data.ballotSnapshots(), ExportFormat.BallotSnapshot::id, "Abstimmungssnapshot");
        Set<String> snapshotNumbers = new HashSet<>();
        for (ExportFormat.BallotSnapshot snapshot : data.ballotSnapshots()) {
            requireReference(showIds, snapshot.mottoShowId(), "Ein Abstimmungssnapshot verweist auf eine unbekannte Mottoshow.");
            requireText(snapshot.createdAt(), "Ein Abstimmungssnapshot ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            if (snapshot.snapshotNumber() < 1 || !snapshotNumbers.add(snapshot.mottoShowId() + ":" + snapshot.snapshotNumber())) {
                throw invalid("Die Nummer eines Abstimmungssnapshots ist nicht g\u00fcltig.");
            }
        }
        uniquePositive(data.ballotSnapshotItems(), ExportFormat.BallotSnapshotItem::id, "Abstimmungssnapshot-Element");
        Map<Long, List<ExportFormat.BallotSnapshotItem>> itemsBySnapshot = new HashMap<>();
        Set<String> snapshotRanks = new HashSet<>();
        Set<String> snapshotEntries = new HashSet<>();
        for (ExportFormat.BallotSnapshotItem item : data.ballotSnapshotItems()) {
            requireReference(snapshotIds, item.ballotSnapshotId(), "Ein Abstimmungssnapshot-Element verweist auf einen unbekannten Snapshot.");
            if (item.contestEntryId() != null) requireReference(entryIds, item.contestEntryId(), "Ein Abstimmungssnapshot-Element verweist auf einen unbekannten Wettbewerbsbeitrag.");
            requireText(item.artistSnapshot(), "Ein Abstimmungssnapshot-Element ohne Interpret ist nicht g\u00fcltig.");
            requireText(item.titleSnapshot(), "Ein Abstimmungssnapshot-Element ohne Titel ist nicht g\u00fcltig.");
            requireText(item.youtubeUrlSnapshot(), "Ein Abstimmungssnapshot-Element ohne YouTube-URL ist nicht g\u00fcltig.");
            if (item.rank() < 1 || item.rank() > 15 || !snapshotRanks.add(item.ballotSnapshotId() + ":" + item.rank())
                    || (item.contestEntryId() != null && !snapshotEntries.add(item.ballotSnapshotId() + ":" + item.contestEntryId()))) {
                throw invalid("Die Top-15-Positionen eines Abstimmungssnapshots sind nicht g\u00fcltig.");
            }
            itemsBySnapshot.computeIfAbsent(item.ballotSnapshotId(), ignored -> new ArrayList<>()).add(item);
        }

        uniquePositive(data.receivedScores(), ExportFormat.ReceivedScore::id, "Ergebniseintrag");
        Map<String, ExportFormat.ReceivedScore> scoresByShowAndParticipant = new HashMap<>();
        for (ExportFormat.ReceivedScore score : data.receivedScores()) {
            requireReference(showIds, score.mottoShowId(), "Ein Ergebniseintrag verweist auf eine unbekannte Mottoshow.");
            requireReference(participantIds, score.participantId(), "Ein Ergebniseintrag verweist auf einen unbekannten Teilnehmer.");
            requireText(score.createdAt(), "Ein Ergebniseintrag ohne Erstellungszeitpunkt ist nicht g\u00fcltig.");
            requireText(score.updatedAt(), "Ein Ergebniseintrag ohne Aktualisierungszeitpunkt ist nicht g\u00fcltig.");
            if (!enumValue(ReceivedScoreStatus.class, score.status())
                    || ("ABGESTIMMT".equals(score.status()) && (score.points() == null || !CscPoints.isAllowedReceivedScore(score.points())))
                    || (!"ABGESTIMMT".equals(score.status()) && score.points() != null)) {
                throw invalid("Der Abstimmungsstatus oder die Punktzahl eines Ergebniseintrags ist nicht g\u00fcltig.");
            }
            if (scoresByShowAndParticipant.putIfAbsent(key(score.mottoShowId(), score.participantId()), score) != null) {
                throw invalid("Ein Teilnehmer darf pro Mottoshow nur einen Ergebniseintrag besitzen.");
            }
        }

        for (ExportFormat.MottoShow show : data.mottoShows()) {
            List<ExportFormat.BallotSnapshot> currentSnapshots = data.ballotSnapshots().stream()
                    .filter(snapshot -> snapshot.mottoShowId() == show.id() && snapshot.current()).toList();
            if (currentSnapshots.size() > 1 || (show.ballotClosedAt() == null && !currentSnapshots.isEmpty())) {
                throw invalid("Der Abstimmungsstatus und die aktuellen Snapshots sind nicht konsistent.");
            }
            if (show.ballotClosedAt() != null && (currentSnapshots.size() != 1
                    || itemsBySnapshot.getOrDefault(currentSnapshots.getFirst().id(), List.of()).size() != 15)) {
                throw invalid("Eine abgeschlossene Abstimmung ben\u00f6tigt genau einen vollst\u00e4ndigen Top-15-Snapshot.");
            }
            if (show.resultsClosedAt() != null) {
                if (show.selectedCandidateId() == null || show.finalPlace() == null) {
                    throw invalid("Abgeschlossene Ergebnisse ben\u00f6tigen Einreichung und Endplatzierung.");
                }
                for (ExportFormat.Participant participant : participantsById.values()) {
                    if (participant.active()) {
                        ExportFormat.ReceivedScore score = scoresByShowAndParticipant.get(key(show.id(), participant.id()));
                        if (score == null || "UNBEKANNT".equals(score.status())) {
                            throw invalid("Abgeschlossene Ergebnisse d\u00fcrfen keine unbekannten aktiven Teilnehmer enthalten.");
                        }
                    }
                }
            }
        }
    }

    private static <T> Set<Long> uniquePositive(List<T> values, ToLongFunction<T> id, String entity) {
        Set<Long> ids = new HashSet<>();
        for (T value : values) {
            long current = id.applyAsLong(value);
            if (current < 1 || !ids.add(current)) throw invalid("Die IDs f\u00fcr " + entity + " m\u00fcssen positiv und eindeutig sein.");
        }
        return ids;
    }

    private static void requireLists(ExportFormat.Data data) {
        if (data.mottoShows() == null || data.candidates() == null || data.participants() == null || data.participantAliases() == null
                || data.contestEntries() == null || data.ballotSnapshots() == null || data.ballotSnapshotItems() == null || data.receivedScores() == null) {
            throw invalid("Der JSON-Export enth\u00e4lt keine vollst\u00e4ndigen fachlichen Daten.");
        }
    }

    private static void requireUtcInstant(String value, String message) {
        try {
            if (value == null || !value.endsWith("Z")) throw new IllegalArgumentException();
            Instant.parse(value);
        } catch (RuntimeException exception) {
            throw invalid(message);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw invalid(message);
    }

    private static void requireReference(Set<Long> ids, long value, String message) {
        if (!ids.contains(value)) throw invalid(message);
    }

    private static <E extends Enum<E>> boolean enumValue(Class<E> type, String value) {
        if (value == null) return false;
        try {
            Enum.valueOf(type, value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static String key(long first, long second) {
        return first + ":" + second;
    }

    private static BackupFileException invalid(String message) {
        return new BackupFileException("EXPORT_INVALID", message);
    }

    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static List<ExportFormat.MottoShow> mottoShows(Connection connection) throws SQLException {
        return query(connection, """
                SELECT id,show_number,name,selected_candidate_id,ballot_closed_at,results_closed_at,final_place,
                       final_place_tied,official_total_points,created_at,updated_at FROM motto_show ORDER BY id
                """, r -> new ExportFormat.MottoShow(r.getLong(1), r.getInt(2), r.getString(3), nullableLong(r, 4),
                r.getString(5), r.getString(6), nullableInt(r, 7), r.getBoolean(8), nullableInt(r, 9), r.getString(10), r.getString(11)));
    }

    private static List<ExportFormat.Candidate> candidates(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at FROM candidate ORDER BY id",
                r -> new ExportFormat.Candidate(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getInt(8), r.getString(9), r.getString(10)));
    }

    private static List<ExportFormat.Participant> participants(Connection connection) throws SQLException {
        return query(connection, "SELECT id,display_name,country_code,active,created_at,updated_at FROM participant ORDER BY id",
                r -> new ExportFormat.Participant(r.getLong(1), r.getString(2), r.getString(3), r.getBoolean(4), r.getString(5), r.getString(6)));
    }

    private static List<ExportFormat.ParticipantAlias> participantAliases(Connection connection) throws SQLException {
        return query(connection, "SELECT id,participant_id,alias FROM participant_alias ORDER BY id",
                r -> new ExportFormat.ParticipantAlias(r.getLong(1), r.getLong(2), r.getString(3)));
    }

    private static List<ExportFormat.ContestEntry> contestEntries(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,artist,title,youtube_url,comment,assessment,assessment_confidence,pool_position,ranking_position,participant_id,created_at,updated_at FROM contest_entry ORDER BY id",
                r -> new ExportFormat.ContestEntry(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), nullableInt(r, 7), nullableInt(r, 8), r.getInt(9), nullableInt(r, 10), nullableLong(r, 11), r.getString(12), r.getString(13)));
    }

    private static ExportFormat.FullExport upgradeV1(ExportFormat.FullExportV1 legacy) {
        if (legacy == null || legacy.data() == null) {
            return new ExportFormat.FullExport(
                    legacy == null ? null : legacy.format(), ExportFormat.VERSION,
                    legacy == null ? null : legacy.exportedAt(), legacy == null ? null : legacy.applicationVersion(),
                    legacy == null ? 0 : legacy.schemaVersion(), null
            );
        }
        ExportFormat.DataV1 data = legacy.data();
        return new ExportFormat.FullExport(
                legacy.format(), ExportFormat.VERSION, legacy.exportedAt(), legacy.applicationVersion(), legacy.schemaVersion(),
                new ExportFormat.Data(
                        data.mottoShows(), data.candidates(), data.participants(), data.participantAliases(),
                        upgradeV1ContestEntries(data.contestEntries()), data.ballotSnapshots(), data.ballotSnapshotItems(), data.receivedScores()
                )
        );
    }

    private static List<ExportFormat.ContestEntry> upgradeV1ContestEntries(List<ExportFormat.ContestEntryV1> entries) {
        if (entries == null) return null;
        Map<Long, List<ExportFormat.ContestEntryV1>> byShow = new HashMap<>();
        for (ExportFormat.ContestEntryV1 entry : entries) {
            if (entry == null) return null;
            byShow.computeIfAbsent(entry.mottoShowId(), ignored -> new ArrayList<>()).add(entry);
        }
        Map<Long, Integer> positionsByEntryId = new HashMap<>();
        for (List<ExportFormat.ContestEntryV1> showEntries : byShow.values()) {
            showEntries.sort(Comparator.comparing(ExportFormat.ContestEntryV1::createdAt, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparingLong(ExportFormat.ContestEntryV1::id));
            for (int index = 0; index < showEntries.size(); index++) {
                positionsByEntryId.put(showEntries.get(index).id(), index + 1);
            }
        }
        return entries.stream().map(entry -> new ExportFormat.ContestEntry(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                migratedAssessment(entry.listened()), migratedConfidence(entry.listened(), entry.relisten()), positionsByEntryId.get(entry.id()), entry.rankingPosition(),
                entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
    }

    private static ExportFormat.FullExport upgradeV2(ExportFormat.FullExportV2 legacy) {
        if (legacy == null || legacy.data() == null) {
            return new ExportFormat.FullExport(
                    legacy == null ? null : legacy.format(), ExportFormat.VERSION,
                    legacy == null ? null : legacy.exportedAt(), legacy == null ? null : legacy.applicationVersion(),
                    legacy == null ? 0 : legacy.schemaVersion(), null
            );
        }
        ExportFormat.DataV2 data = legacy.data();
        return new ExportFormat.FullExport(
                legacy.format(), ExportFormat.VERSION, legacy.exportedAt(), legacy.applicationVersion(), legacy.schemaVersion(),
                new ExportFormat.Data(
                        data.mottoShows(), data.candidates(), data.participants(), data.participantAliases(),
                        upgradeV2ContestEntries(data.contestEntries()), data.ballotSnapshots(), data.ballotSnapshotItems(), data.receivedScores()
                )
        );
    }

    private static List<ExportFormat.ContestEntry> upgradeV2ContestEntries(List<ExportFormat.ContestEntryV2> entries) {
        if (entries == null) return null;
        return entries.stream().map(entry -> entry == null ? null : new ExportFormat.ContestEntry(
                entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                migratedAssessment(entry.listened()), migratedConfidence(entry.listened(), entry.relisten()), entry.poolPosition(), entry.rankingPosition(),
                entry.participantId(), entry.createdAt(), entry.updatedAt()
        )).toList();
    }

    /** Uses the same conservative flag mapping as the schema-8 SQLite migration. */
    private static Integer migratedAssessment(boolean listened) {
        return listened ? 3 : null;
    }

    private static Integer migratedConfidence(boolean listened, boolean relisten) {
        if (!listened) return null;
        return relisten ? 1 : 2;
    }

    private static boolean validAssessmentPair(Integer assessment, Integer assessmentConfidence) {
        if (assessment == null || assessmentConfidence == null) {
            return assessment == null && assessmentConfidence == null;
        }
        return assessment >= 1 && assessment <= 5 && assessmentConfidence >= 1 && assessmentConfidence <= 5;
    }

    private static List<ExportFormat.BallotSnapshot> ballotSnapshots(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,snapshot_number,created_at,is_current FROM ballot_snapshot ORDER BY id",
                r -> new ExportFormat.BallotSnapshot(r.getLong(1), r.getLong(2), r.getInt(3), r.getString(4), r.getBoolean(5)));
    }

    private static List<ExportFormat.BallotSnapshotItem> ballotSnapshotItems(Connection connection) throws SQLException {
        return query(connection, "SELECT id,ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot FROM ballot_snapshot_item ORDER BY id",
                r -> new ExportFormat.BallotSnapshotItem(r.getLong(1), r.getLong(2), r.getInt(3), nullableLong(r, 4), r.getString(5), r.getString(6), r.getString(7)));
    }

    private static List<ExportFormat.ReceivedScore> receivedScores(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,participant_id,status,points,created_at,updated_at FROM received_score ORDER BY id",
                r -> new ExportFormat.ReceivedScore(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), nullableInt(r, 5), r.getString(6), r.getString(7)));
    }

    private static <T> List<T> query(Connection connection, String sql, SqlRowMapper<T> mapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) rows.add(mapper.map(result));
        }
        return rows;
    }

    private static Long nullableLong(ResultSet result, int index) throws SQLException {
        long value = result.getLong(index);
        return result.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet result, int index) throws SQLException {
        int value = result.getInt(index);
        return result.wasNull() ? null : value;
    }

    private String applicationVersion() {
        return backups.applicationVersion();
    }

    @FunctionalInterface
    private interface SqlRowMapper<T> {
        T map(ResultSet result) throws SQLException;
    }
}
