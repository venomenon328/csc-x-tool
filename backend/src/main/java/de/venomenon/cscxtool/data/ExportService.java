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

    private static final long LEGACY_CSC_X_ID = 1L;
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

    /** Reads all tables from one fixed SQLite read snapshot. */
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
                                contests(connection), shows, candidates(connection), participants(connection),
                                contestParticipations(connection), participantAliases(connection), contestEntries(connection),
                                ballotSnapshots(connection), ballotSnapshotItems(connection), receivedScores(connection),
                                publishedBallots(connection), publishedBallotPositions(connection)
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
                throw invalid("Der JSON-Export enthält keine unterstützte Formatversion.");
            }
            ExportFormat.FullExport export = switch (versionNumber.intValue()) {
                case ExportFormat.LEGACY_VERSION -> upgradeV3(upgradeV1(strictMapper.readValue(input, ExportFormat.FullExportV1.class)));
                case ExportFormat.VERSION_2 -> upgradeV3(upgradeV2(strictMapper.readValue(input, ExportFormat.FullExportV2.class)));
                case ExportFormat.VERSION_3 -> upgradeV3(strictMapper.readValue(input, ExportFormat.FullExportV3.class));
                case ExportFormat.VERSION_4 -> upgradeV4(strictMapper.readValue(input, ExportFormat.FullExportV4.class));
                case ExportFormat.VERSION_5 -> upgradeV5(strictMapper.readValue(input, ExportFormat.FullExportV5.class));
                case ExportFormat.VERSION -> strictMapper.readValue(input, ExportFormat.FullExport.class);
                default -> throw invalid("Das JSON-Format wird von dieser Anwendung nicht unterstützt.");
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
            throw new BackupStorageException("Die Staging-Datenbank für den JSON-Import konnte nicht migriert werden.", exception);
        }
        JdbcTemplate stage = new JdbcTemplate(SqliteDataSourceFactory.create(databaseFile));
        ExportFormat.Data data = export.data();
        try {
            stage.update("DELETE FROM published_ballot_position");
            stage.update("DELETE FROM published_ballot");
            stage.update("DELETE FROM received_score");
            stage.update("DELETE FROM ballot_snapshot_item");
            stage.update("DELETE FROM ballot_snapshot");
            stage.update("DELETE FROM contest_entry");
            stage.update("UPDATE motto_show SET selected_candidate_id = NULL");
            stage.update("DELETE FROM candidate");
            stage.update("DELETE FROM contest_participation");
            stage.update("DELETE FROM participant_alias");
            stage.update("DELETE FROM participant");
            stage.update("DELETE FROM motto_show");
            stage.update("DELETE FROM contest");

            for (ExportFormat.Contest row : data.contests()) {
                stage.update("""
                        INSERT INTO contest (id,name,display_order,is_current,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, row.id(), row.name(), row.displayOrder(), row.current(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.MottoShow row : data.mottoShows()) {
                stage.update("""
                        INSERT INTO motto_show (id,contest_id,show_number,name,entry_list_complete,selected_candidate_id,ballot_closed_at,results_closed_at,
                          final_place,final_place_tied,official_total_points,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, NULL, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.contestId(), row.showNumber(), row.name(), row.entryListComplete(), row.ballotClosedAt(), row.resultsClosedAt(),
                        row.finalPlace(), row.finalPlaceTied(), row.officialTotalPoints(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.Participant row : data.participants()) {
                stage.update("INSERT INTO participant (id,display_name,active,created_at,updated_at) VALUES (?, ?, ?, ?, ?)",
                        row.id(), row.displayName(), row.active(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.ContestParticipation row : data.contestParticipations()) {
                stage.update("""
                        INSERT INTO contest_participation (id,contest_id,participant_id,country_code,active,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.contestId(), row.participantId(), row.countryCode(), row.active(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.ParticipantAlias row : data.participantAliases()) {
                stage.update("INSERT INTO participant_alias (id,participant_id,alias) VALUES (?, ?, ?)",
                        row.id(), row.participantId(), row.alias());
            }
            for (ExportFormat.Candidate row : data.candidates()) {
                stage.update("""
                        INSERT INTO candidate (id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.artist(), row.title(), row.youtubeUrl(), row.comment(), row.status(),
                        row.manualPosition(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.ContestEntry row : data.contestEntries()) {
                stage.update("""
                        INSERT INTO contest_entry (id,motto_show_id,contest_id,artist,title,youtube_url,comment,assessment,
                          assessment_confidence,pool_position,ranking_position,contest_participation_id,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.contestId(), row.artist(), row.title(), row.youtubeUrl(), row.comment(),
                        row.assessment(), row.assessmentConfidence(), row.poolPosition(), row.rankingPosition(),
                        row.contestParticipationId(), row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.BallotSnapshot row : data.ballotSnapshots()) {
                stage.update("INSERT INTO ballot_snapshot (id,motto_show_id,snapshot_number,created_at,is_current) VALUES (?, ?, ?, ?, ?)",
                        row.id(), row.mottoShowId(), row.snapshotNumber(), row.createdAt(), row.current());
            }
            for (ExportFormat.BallotSnapshotItem row : data.ballotSnapshotItems()) {
                stage.update("""
                        INSERT INTO ballot_snapshot_item (id,ballot_snapshot_id,rank,contest_entry_id,artist_snapshot,title_snapshot,youtube_url_snapshot)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.ballotSnapshotId(), row.rank(), row.contestEntryId(), row.artistSnapshot(), row.titleSnapshot(), row.youtubeUrlSnapshot());
            }
            for (ExportFormat.ReceivedScore row : data.receivedScores()) {
                stage.update("""
                        INSERT INTO received_score (id,motto_show_id,contest_id,contest_participation_id,status,points,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.contestId(), row.contestParticipationId(), row.status(), row.points(),
                        row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.PublishedBallot row : data.publishedBallots()) {
                stage.update("""
                        INSERT INTO published_ballot (id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, row.id(), row.mottoShowId(), row.contestId(), row.contestParticipationId(), row.status(),
                        row.createdAt(), row.updatedAt());
            }
            for (ExportFormat.PublishedBallotPosition row : data.publishedBallotPositions()) {
                stage.update("""
                        INSERT INTO published_ballot_position (id,published_ballot_id,contest_entry_id,rank)
                        VALUES (?, ?, ?, ?)
                        """, row.id(), row.publishedBallotId(), row.contestEntryId(), row.rank());
            }
            for (ExportFormat.MottoShow row : data.mottoShows()) {
                stage.update("UPDATE motto_show SET selected_candidate_id = ? WHERE id = ?", row.selectedCandidateId(), row.id());
            }
        } catch (DataAccessException exception) {
            throw new BackupStorageException("Die vorgeprüften JSON-Daten konnten nicht in die Staging-Datenbank geschrieben werden.", exception);
        }
        try {
            SchemaSupport.verify(databaseFile, SchemaSupport.CURRENT_SCHEMA_VERSION);
        } catch (BackupFileException exception) {
            throw new BackupStorageException("Die vorbereitete JSON-Wiederherstellung besteht die SQLite-Prüfung nicht.", exception);
        }
    }

    private void validateExport(ExportFormat.FullExport export) {
        if (export == null || !ExportFormat.FORMAT.equals(export.format()) || export.formatVersion() != ExportFormat.VERSION) {
            throw invalid("Das JSON-Format wird von dieser Anwendung nicht unterstützt.");
        }
        if (export.schemaVersion() < 1 || export.schemaVersion() > SchemaSupport.CURRENT_SCHEMA_VERSION) {
            throw invalid("Der JSON-Export enthält keine unterstützte Datenbankschemaversion.");
        }
        requireUtcInstant(export.exportedAt(), "Der JSON-Export enthält keinen gültigen UTC-Exportzeitpunkt.");
        requireText(export.applicationVersion(), "Der JSON-Export enthält keine Anwendungsversionsangabe.");
        if (export.data() == null) throw invalid("Der JSON-Export enthält keine vollständigen fachlichen Daten.");
        ExportFormat.Data data = export.data();
        if (data.contests() == null || data.mottoShows() == null || data.candidates() == null || data.participants() == null
                || data.contestParticipations() == null || data.participantAliases() == null || data.contestEntries() == null
                || data.ballotSnapshots() == null || data.ballotSnapshotItems() == null || data.receivedScores() == null
                || data.publishedBallots() == null || data.publishedBallotPositions() == null) {
            throw invalid("Der JSON-Export enthält keine vollständigen fachlichen Daten.");
        }
        Set<Long> contestIds = uniquePositive(data.contests(), ExportFormat.Contest::id, "CSC-Ausgabe");
        Map<Long, ExportFormat.Contest> contestsById = new HashMap<>();
        Set<String> contestNames = new HashSet<>();
        Set<Integer> contestOrders = new HashSet<>();
        int currentContests = 0;
        for (ExportFormat.Contest contest : data.contests()) {
            requireText(contest.name(), "Eine CSC-Ausgabe ohne Namen ist nicht gültig.");
            requireText(contest.createdAt(), "Eine CSC-Ausgabe ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(contest.updatedAt(), "Eine CSC-Ausgabe ohne Änderungszeitpunkt ist nicht gültig.");
            if (!contestNames.add(contest.name().trim().toLowerCase(java.util.Locale.ROOT)) || contest.displayOrder() < 1
                    || !contestOrders.add(contest.displayOrder())) throw invalid("Contestname oder Anzeigereihenfolge ist nicht gültig.");
            if (contest.current()) currentContests++;
            contestsById.put(contest.id(), contest);
        }
        if (data.contests().isEmpty() || currentContests != 1) throw invalid("Der Export benötigt genau eine aktuelle CSC-Ausgabe.");

        Set<Long> showIds = uniquePositive(data.mottoShows(), ExportFormat.MottoShow::id, "Mottoshow");
        Map<Long, ExportFormat.MottoShow> shows = new HashMap<>();
        Set<String> showNumbers = new HashSet<>();
        for (ExportFormat.MottoShow show : data.mottoShows()) {
            requireReference(contestIds, show.contestId(), "Eine Mottoshow verweist auf eine unbekannte CSC-Ausgabe.");
            requireText(show.name(), "Eine Mottoshow ohne Namen ist nicht gültig.");
            requireText(show.createdAt(), "Eine Mottoshow ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(show.updatedAt(), "Eine Mottoshow ohne Änderungszeitpunkt ist nicht gültig.");
            if (show.showNumber() < 1 || !showNumbers.add(key(show.contestId(), show.showNumber()))) {
                throw invalid("Mottoshownummern müssen pro CSC-Ausgabe positiv und eindeutig sein.");
            }
            if (show.finalPlaceTied() && show.finalPlace() == null) {
                throw invalid("Eine geteilte Endplatzierung benötigt eine Endplatzierung.");
            }
            if (show.resultsClosedAt() != null && show.ballotClosedAt() == null) {
                throw invalid("Abgeschlossene Ergebnisse benötigen eine abgeschlossene Abstimmung.");
            }
            shows.put(show.id(), show);
        }
        Set<Long> participantIds = uniquePositive(data.participants(), ExportFormat.Participant::id, "Teilnehmer");
        for (ExportFormat.Participant participant : data.participants()) {
            requireText(participant.displayName(), "Ein Teilnehmer ohne Anzeigename ist nicht gültig.");
            requireText(participant.createdAt(), "Ein Teilnehmer ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(participant.updatedAt(), "Ein Teilnehmer ohne Änderungszeitpunkt ist nicht gültig.");
        }
        Set<Long> participationIds = uniquePositive(data.contestParticipations(), ExportFormat.ContestParticipation::id, "Contest-Teilnahme");
        Map<Long, ExportFormat.ContestParticipation> participations = new HashMap<>();
        Set<String> participationKeys = new HashSet<>();
        for (ExportFormat.ContestParticipation participation : data.contestParticipations()) {
            requireReference(contestIds, participation.contestId(), "Eine Contest-Teilnahme verweist auf eine unbekannte CSC-Ausgabe.");
            requireReference(participantIds, participation.participantId(), "Eine Contest-Teilnahme verweist auf einen unbekannten Teilnehmer.");
            if (!validCountryCodes.contains(participation.countryCode())) throw invalid("Der JSON-Export enthält einen nicht unterstützten Ländercode.");
            requireText(participation.createdAt(), "Eine Contest-Teilnahme ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(participation.updatedAt(), "Eine Contest-Teilnahme ohne Änderungszeitpunkt ist nicht gültig.");
            if (!participationKeys.add(key(participation.contestId(), participation.participantId()))) {
                throw invalid("Ein Teilnehmer darf pro CSC-Ausgabe nur einmal teilnehmen.");
            }
            participations.put(participation.id(), participation);
        }
        uniquePositive(data.participantAliases(), ExportFormat.ParticipantAlias::id, "Teilnehmeralias");
        for (ExportFormat.ParticipantAlias alias : data.participantAliases()) {
            requireReference(participantIds, alias.participantId(), "Ein Teilnehmeralias verweist auf einen unbekannten Teilnehmer.");
            requireText(alias.alias(), "Ein leerer Teilnehmeralias ist nicht gültig.");
        }

        Set<Long> candidateIds = uniquePositive(data.candidates(), ExportFormat.Candidate::id, "Kandidat");
        Map<Long, ExportFormat.Candidate> candidates = new HashMap<>();
        Set<String> candidatePositions = new HashSet<>();
        for (ExportFormat.Candidate candidate : data.candidates()) {
            requireReference(showIds, candidate.mottoShowId(), "Ein Kandidat verweist auf eine unbekannte Mottoshow.");
            requireText(candidate.artist(), "Ein Kandidat ohne Interpret ist nicht gültig.");
            requireText(candidate.title(), "Ein Kandidat ohne Titel ist nicht gültig.");
            requireText(candidate.youtubeUrl(), "Ein Kandidat ohne YouTube-URL ist nicht gültig.");
            requireText(candidate.createdAt(), "Ein Kandidat ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(candidate.updatedAt(), "Ein Kandidat ohne Aktualisierungszeitpunkt ist nicht gültig.");
            if (!enumValue(CandidateStatus.class, candidate.status()) || candidate.manualPosition() < 1
                    || !candidatePositions.add(key(candidate.mottoShowId(), candidate.manualPosition()))) {
                throw invalid("Die Kandidatenreihenfolge oder der Kandidatenstatus ist nicht gültig.");
            }
            candidates.put(candidate.id(), candidate);
        }
        for (ExportFormat.MottoShow show : data.mottoShows()) {
            if (show.selectedCandidateId() != null) {
                ExportFormat.Candidate candidate = candidates.get(show.selectedCandidateId());
                if (candidate == null || candidate.mottoShowId() != show.id()) throw invalid("Die ausgewählte Einreichung muss zu ihrer Mottoshow gehören.");
            }
        }

        Set<Long> entryIds = uniquePositive(data.contestEntries(), ExportFormat.ContestEntry::id, "Wettbewerbsbeitrag");
        Set<String> poolPositions = new HashSet<>();
        Set<String> rankingPositions = new HashSet<>();
        Set<String> entryParticipants = new HashSet<>();
        Map<Long, ExportFormat.ContestEntry> entries = new HashMap<>();
        Map<Long, List<Integer>> poolPositionsByShow = new HashMap<>();
        for (ExportFormat.ContestEntry entry : data.contestEntries()) {
            ExportFormat.MottoShow show = shows.get(entry.mottoShowId());
            if (show == null || show.contestId() != entry.contestId()) throw invalid("Ein Wettbewerbsbeitrag gehört nicht zum Contest seiner Mottoshow.");
            requireText(entry.artist(), "Ein Wettbewerbsbeitrag ohne Interpret ist nicht gültig.");
            requireText(entry.title(), "Ein Wettbewerbsbeitrag ohne Titel ist nicht gültig.");
            if (contestsById.get(entry.contestId()).current()) {
                requireText(entry.youtubeUrl(), "Ein Wettbewerbsbeitrag ohne YouTube-URL ist nicht gültig.");
            }
            requireText(entry.createdAt(), "Ein Wettbewerbsbeitrag ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(entry.updatedAt(), "Ein Wettbewerbsbeitrag ohne Aktualisierungszeitpunkt ist nicht gültig.");
            if (!validAssessmentPair(entry.assessment(), entry.assessmentConfidence()) || entry.poolPosition() < 1
                    || !poolPositions.add(key(entry.mottoShowId(), entry.poolPosition()))
                    || (entry.rankingPosition() != null && (entry.rankingPosition() < 1 || !rankingPositions.add(key(entry.mottoShowId(), entry.rankingPosition()))))) {
                throw invalid("Die Reihenfolge oder Einschätzung eines Wettbewerbsbeitrags ist nicht gültig.");
            }
            if (entry.contestParticipationId() != null) {
                ExportFormat.ContestParticipation participation = participations.get(entry.contestParticipationId());
                if (participation == null || participation.contestId() != entry.contestId()
                        || !entryParticipants.add(key(entry.mottoShowId(), participation.id()))) {
                    throw invalid("Die Teilnehmerzuordnung eines Wettbewerbsbeitrags ist nicht gültig.");
                }
            }
            entries.put(entry.id(), entry);
            poolPositionsByShow.computeIfAbsent(entry.mottoShowId(), ignored -> new ArrayList<>()).add(entry.poolPosition());
        }
        for (List<Integer> positions : poolPositionsByShow.values()) {
            positions.sort(Comparator.naturalOrder());
            for (int index = 0; index < positions.size(); index++) {
                if (positions.get(index) != index + 1) {
                    throw invalid("Die manuelle Reihenfolge der Wettbewerbsbeiträge muss lückenlos sein.");
                }
            }
        }
        for (ExportFormat.MottoShow show : data.mottoShows()) {
            if (!show.entryListComplete()) continue;
            if (contestsById.get(show.contestId()).current()) {
                throw invalid("Nur historische Mottoshows können eine vollständige Songliste bestätigen.");
            }
            List<ExportFormat.ContestEntry> showEntries = data.contestEntries().stream()
                    .filter(entry -> entry.mottoShowId() == show.id()).toList();
            if (showEntries.isEmpty() || showEntries.stream().anyMatch(entry -> entry.contestParticipationId() == null)) {
                throw invalid("Eine vollständige historische Songliste benötigt mindestens einen vollständig zugeordneten Beitrag.");
            }
        }

        Set<Long> snapshotIds = uniquePositive(data.ballotSnapshots(), ExportFormat.BallotSnapshot::id, "Abstimmungssnapshot");
        Map<Long, ExportFormat.BallotSnapshot> snapshots = new HashMap<>();
        Set<String> snapshotNumbers = new HashSet<>();
        for (ExportFormat.BallotSnapshot snapshot : data.ballotSnapshots()) {
            requireReference(showIds, snapshot.mottoShowId(), "Ein Abstimmungssnapshot verweist auf eine unbekannte Mottoshow.");
            requireText(snapshot.createdAt(), "Ein Abstimmungssnapshot ohne Erstellungszeitpunkt ist nicht gültig.");
            if (snapshot.snapshotNumber() < 1 || !snapshotNumbers.add(key(snapshot.mottoShowId(), snapshot.snapshotNumber()))) {
                throw invalid("Die Nummer eines Abstimmungssnapshots ist nicht gültig.");
            }
            snapshots.put(snapshot.id(), snapshot);
        }
        uniquePositive(data.ballotSnapshotItems(), ExportFormat.BallotSnapshotItem::id, "Abstimmungssnapshot-Element");
        Map<Long, List<ExportFormat.BallotSnapshotItem>> itemsBySnapshot = new HashMap<>();
        Set<String> snapshotRanks = new HashSet<>();
        Set<String> snapshotEntries = new HashSet<>();
        for (ExportFormat.BallotSnapshotItem item : data.ballotSnapshotItems()) {
            requireReference(snapshotIds, item.ballotSnapshotId(), "Ein Abstimmungssnapshot-Element verweist auf einen unbekannten Snapshot.");
            if (item.contestEntryId() != null) {
                requireReference(entryIds, item.contestEntryId(), "Ein Abstimmungssnapshot-Element verweist auf einen unbekannten Wettbewerbsbeitrag.");
                if (entries.get(item.contestEntryId()).mottoShowId() != snapshots.get(item.ballotSnapshotId()).mottoShowId()) {
                    throw invalid("Ein Abstimmungssnapshot-Element verweist auf einen Wettbewerbsbeitrag einer anderen Mottoshow.");
                }
            }
            if (item.rank() < 1 || item.rank() > 15 || !snapshotRanks.add(key(item.ballotSnapshotId(), item.rank()))
                    || (item.contestEntryId() != null && !snapshotEntries.add(key(item.ballotSnapshotId(), item.contestEntryId())))) {
                throw invalid("Die Top-15-Positionen eines Abstimmungssnapshots sind nicht gültig.");
            }
            requireText(item.artistSnapshot(), "Ein Abstimmungssnapshot-Element ohne Interpret ist nicht gültig.");
            requireText(item.titleSnapshot(), "Ein Abstimmungssnapshot-Element ohne Titel ist nicht gültig.");
            requireText(item.youtubeUrlSnapshot(), "Ein Abstimmungssnapshot-Element ohne YouTube-URL ist nicht gültig.");
            itemsBySnapshot.computeIfAbsent(item.ballotSnapshotId(), ignored -> new ArrayList<>()).add(item);
        }

        uniquePositive(data.receivedScores(), ExportFormat.ReceivedScore::id, "Ergebniseintrag");
        Map<String, ExportFormat.ReceivedScore> scoresByShowAndParticipation = new HashMap<>();
        for (ExportFormat.ReceivedScore score : data.receivedScores()) {
            ExportFormat.MottoShow show = shows.get(score.mottoShowId());
            ExportFormat.ContestParticipation participation = participations.get(score.contestParticipationId());
            if (show == null || participation == null || score.contestId() != show.contestId() || score.contestId() != participation.contestId()) {
                throw invalid("Ein Ergebniseintrag verweist contestfremd.");
            }
            if (!enumValue(ReceivedScoreStatus.class, score.status())
                    || ("ABGESTIMMT".equals(score.status()) && (score.points() == null || !CscPoints.isAllowedReceivedScore(score.points())))
                    || (!"ABGESTIMMT".equals(score.status()) && score.points() != null)) {
                throw invalid("Der Abstimmungsstatus oder die Punktzahl eines Ergebniseintrags ist nicht gültig.");
            }
            requireText(score.createdAt(), "Ein Ergebniseintrag ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(score.updatedAt(), "Ein Ergebniseintrag ohne Aktualisierungszeitpunkt ist nicht gültig.");
            if (scoresByShowAndParticipation.putIfAbsent(key(score.mottoShowId(), score.contestParticipationId()), score) != null) {
                throw invalid("Ein Teilnehmer darf pro Mottoshow nur einen Ergebniseintrag besitzen.");
            }
        }

        Set<Long> publishedBallotIds = uniquePositive(data.publishedBallots(), ExportFormat.PublishedBallot::id, "Veröffentlichter Stimmzettel");
        Map<Long, ExportFormat.PublishedBallot> publishedBallots = new HashMap<>();
        Set<String> publishedBallotKeys = new HashSet<>();
        for (ExportFormat.PublishedBallot ballot : data.publishedBallots()) {
            ExportFormat.MottoShow show = shows.get(ballot.mottoShowId());
            ExportFormat.ContestParticipation participation = participations.get(ballot.contestParticipationId());
            if (show == null || participation == null || ballot.contestId() != show.contestId()
                    || ballot.contestId() != participation.contestId()
                    || !("ABGESTIMMT".equals(ballot.status()) || "NICHT_ABGESTIMMT".equals(ballot.status()))
                    || !publishedBallotKeys.add(key(ballot.mottoShowId(), ballot.contestParticipationId()))) {
                throw invalid("Ein veröffentlichter Stimmzettel ist contestfremd oder besitzt keinen gültigen Status.");
            }
            requireText(ballot.createdAt(), "Ein veröffentlichter Stimmzettel ohne Erstellungszeitpunkt ist nicht gültig.");
            requireText(ballot.updatedAt(), "Ein veröffentlichter Stimmzettel ohne Änderungszeitpunkt ist nicht gültig.");
            publishedBallots.put(ballot.id(), ballot);
        }
        uniquePositive(data.publishedBallotPositions(), ExportFormat.PublishedBallotPosition::id, "Stimmzettelposition");
        Map<Long, List<ExportFormat.PublishedBallotPosition>> positionsByPublishedBallot = new HashMap<>();
        Set<String> publishedRanks = new HashSet<>();
        Set<String> publishedEntries = new HashSet<>();
        for (ExportFormat.PublishedBallotPosition position : data.publishedBallotPositions()) {
            ExportFormat.PublishedBallot ballot = publishedBallots.get(position.publishedBallotId());
            ExportFormat.ContestEntry entry = entries.get(position.contestEntryId());
            if (ballot == null || entry == null || entry.mottoShowId() != ballot.mottoShowId()
                    || position.rank() < 1 || position.rank() > 15
                    || !publishedRanks.add(key(position.publishedBallotId(), position.rank()))
                    || !publishedEntries.add(key(position.publishedBallotId(), position.contestEntryId()))
                    || (entry.contestParticipationId() != null && ballot.contestParticipationId() == entry.contestParticipationId())) {
                throw invalid("Eine veröffentlichte Stimmzettelposition ist nicht eindeutig oder nicht wählbar.");
            }
            positionsByPublishedBallot.computeIfAbsent(position.publishedBallotId(), ignored -> new ArrayList<>()).add(position);
        }
        for (ExportFormat.PublishedBallot ballot : data.publishedBallots()) {
            List<ExportFormat.PublishedBallotPosition> positions = positionsByPublishedBallot.getOrDefault(ballot.id(), List.of());
            if (("ABGESTIMMT".equals(ballot.status()) && positions.size() != 15)
                    || ("NICHT_ABGESTIMMT".equals(ballot.status()) && !positions.isEmpty())) {
                throw invalid("Ein abgegebener Stimmzettel benötigt genau 15 Positionen; nicht abgestimmt besitzt keine Position.");
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
                throw invalid("Eine abgeschlossene Abstimmung benötigt genau einen vollständigen Top-15-Snapshot.");
            }
            if (show.resultsClosedAt() != null) {
                if (show.selectedCandidateId() == null || show.finalPlace() == null) {
                    throw invalid("Abgeschlossene Ergebnisse benötigen Einreichung und Endplatzierung.");
                }
                for (ExportFormat.ContestParticipation participation : participations.values()) {
                    if (participation.contestId() == show.contestId() && participation.active()) {
                        ExportFormat.ReceivedScore score = scoresByShowAndParticipation.get(key(show.id(), participation.id()));
                        if (score == null || "UNBEKANNT".equals(score.status())) {
                            throw invalid("Abgeschlossene Ergebnisse dürfen keine unbekannten aktiven Teilnehmer enthalten.");
                        }
                    }
                }
            }
        }
    }

    private static ExportFormat.FullExportV3 upgradeV1(ExportFormat.FullExportV1 legacy) {
        if (legacy == null || legacy.data() == null) return new ExportFormat.FullExportV3(
                legacy == null ? null : legacy.format(), ExportFormat.VERSION_3, legacy == null ? null : legacy.exportedAt(),
                legacy == null ? null : legacy.applicationVersion(), legacy == null ? 0 : legacy.schemaVersion(), null);
        ExportFormat.DataV1 data = legacy.data();
        Map<Long, List<ExportFormat.ContestEntryV1>> byShow = new HashMap<>();
        if (data.contestEntries() != null) for (ExportFormat.ContestEntryV1 entry : data.contestEntries()) {
            if (entry == null) continue;
            byShow.computeIfAbsent(entry.mottoShowId(), ignored -> new ArrayList<>()).add(entry);
        }
        Map<Long, Integer> positions = new HashMap<>();
        for (List<ExportFormat.ContestEntryV1> entries : byShow.values()) {
            entries.sort(Comparator.comparing(ExportFormat.ContestEntryV1::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(ExportFormat.ContestEntryV1::id));
            for (int i = 0; i < entries.size(); i++) positions.put(entries.get(i).id(), i + 1);
        }
        List<ExportFormat.ContestEntryV3> entries = data.contestEntries() == null ? null : data.contestEntries().stream().map(entry ->
                new ExportFormat.ContestEntryV3(entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                        migratedAssessment(entry.listened()), migratedConfidence(entry.listened(), entry.relisten()), positions.get(entry.id()),
                        entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt())).toList();
        return new ExportFormat.FullExportV3(legacy.format(), ExportFormat.VERSION_3, legacy.exportedAt(), legacy.applicationVersion(),
                legacy.schemaVersion(), new ExportFormat.DataV3(data.mottoShows(), data.candidates(), data.participants(), data.participantAliases(),
                entries, data.ballotSnapshots(), data.ballotSnapshotItems(), data.receivedScores()));
    }

    private static ExportFormat.FullExportV3 upgradeV2(ExportFormat.FullExportV2 legacy) {
        if (legacy == null || legacy.data() == null) return new ExportFormat.FullExportV3(
                legacy == null ? null : legacy.format(), ExportFormat.VERSION_3, legacy == null ? null : legacy.exportedAt(),
                legacy == null ? null : legacy.applicationVersion(), legacy == null ? 0 : legacy.schemaVersion(), null);
        ExportFormat.DataV2 data = legacy.data();
        List<ExportFormat.ContestEntryV3> entries = data.contestEntries() == null ? null : data.contestEntries().stream().map(entry ->
                new ExportFormat.ContestEntryV3(entry.id(), entry.mottoShowId(), entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(),
                        migratedAssessment(entry.listened()), migratedConfidence(entry.listened(), entry.relisten()), entry.poolPosition(),
                        entry.rankingPosition(), entry.participantId(), entry.createdAt(), entry.updatedAt())).toList();
        return new ExportFormat.FullExportV3(legacy.format(), ExportFormat.VERSION_3, legacy.exportedAt(), legacy.applicationVersion(),
                legacy.schemaVersion(), new ExportFormat.DataV3(data.mottoShows(), data.candidates(), data.participants(), data.participantAliases(),
                entries, data.ballotSnapshots(), data.ballotSnapshotItems(), data.receivedScores()));
    }

    private static ExportFormat.FullExport upgradeV3(ExportFormat.FullExportV3 legacy) {
        if (legacy == null || legacy.data() == null) return new ExportFormat.FullExport(
                legacy == null ? null : legacy.format(), ExportFormat.VERSION, legacy == null ? null : legacy.exportedAt(),
                legacy == null ? null : legacy.applicationVersion(), legacy == null ? 0 : legacy.schemaVersion(), null);
        ExportFormat.DataV3 data = legacy.data();
        String timestamp = legacy.exportedAt();
        List<ExportFormat.MottoShow> shows = data.mottoShows() == null ? null : data.mottoShows().stream().map(show ->
                new ExportFormat.MottoShow(show.id(), LEGACY_CSC_X_ID, show.showNumber(), show.name(), false, show.selectedCandidateId(),
                        show.ballotClosedAt(), show.resultsClosedAt(), show.finalPlace(), show.finalPlaceTied(),
                        show.officialTotalPoints(), show.createdAt(), show.updatedAt())).toList();
        List<ExportFormat.Participant> participants = data.participants() == null ? null : data.participants().stream().map(participant ->
                new ExportFormat.Participant(participant.id(), participant.displayName(), participant.active(), participant.createdAt(), participant.updatedAt())).toList();
        List<ExportFormat.ContestParticipation> participations = data.participants() == null ? null : data.participants().stream().map(participant ->
                new ExportFormat.ContestParticipation(participant.id(), LEGACY_CSC_X_ID, participant.id(), participant.countryCode(),
                        participant.active(), participant.createdAt(), participant.updatedAt())).toList();
        List<ExportFormat.ContestEntry> entries = data.contestEntries() == null ? null : data.contestEntries().stream().map(entry ->
                new ExportFormat.ContestEntry(entry.id(), entry.mottoShowId(), LEGACY_CSC_X_ID, entry.artist(), entry.title(), entry.youtubeUrl(),
                        entry.comment(), entry.assessment(), entry.assessmentConfidence(), entry.poolPosition(), entry.rankingPosition(),
                        entry.participantId(), entry.createdAt(), entry.updatedAt())).toList();
        List<ExportFormat.ReceivedScore> scores = data.receivedScores() == null ? null : data.receivedScores().stream().map(score ->
                new ExportFormat.ReceivedScore(score.id(), score.mottoShowId(), LEGACY_CSC_X_ID, score.participantId(), score.status(),
                        score.points(), score.createdAt(), score.updatedAt())).toList();
        return new ExportFormat.FullExport(legacy.format(), ExportFormat.VERSION, legacy.exportedAt(), legacy.applicationVersion(), legacy.schemaVersion(),
                new ExportFormat.Data(List.of(new ExportFormat.Contest(LEGACY_CSC_X_ID, "CSC X", 1, true, timestamp, timestamp)),
                        shows, data.candidates(), participants, participations, data.participantAliases(), entries,
                        data.ballotSnapshots(), data.ballotSnapshotItems(), scores));
    }

    private static ExportFormat.FullExport upgradeV4(ExportFormat.FullExportV4 legacy) {
        if (legacy == null || legacy.data() == null) return new ExportFormat.FullExport(
                legacy == null ? null : legacy.format(), ExportFormat.VERSION, legacy == null ? null : legacy.exportedAt(),
                legacy == null ? null : legacy.applicationVersion(), legacy == null ? 0 : legacy.schemaVersion(), null);
        ExportFormat.DataV4 data = legacy.data();
        List<ExportFormat.MottoShow> shows = data.mottoShows() == null ? null : data.mottoShows().stream().map(show ->
                new ExportFormat.MottoShow(show.id(), show.contestId(), show.showNumber(), show.name(), false,
                        show.selectedCandidateId(), show.ballotClosedAt(), show.resultsClosedAt(), show.finalPlace(),
                        show.finalPlaceTied(), show.officialTotalPoints(), show.createdAt(), show.updatedAt())
        ).toList();
        return new ExportFormat.FullExport(legacy.format(), ExportFormat.VERSION, legacy.exportedAt(), legacy.applicationVersion(),
                legacy.schemaVersion(), new ExportFormat.Data(data.contests(), shows, data.candidates(), data.participants(),
                        data.contestParticipations(), data.participantAliases(), data.contestEntries(), data.ballotSnapshots(),
                        data.ballotSnapshotItems(), data.receivedScores()));
    }

    private static ExportFormat.FullExport upgradeV5(ExportFormat.FullExportV5 legacy) {
        if (legacy == null || legacy.data() == null) return new ExportFormat.FullExport(
                legacy == null ? null : legacy.format(), ExportFormat.VERSION, legacy == null ? null : legacy.exportedAt(),
                legacy == null ? null : legacy.applicationVersion(), legacy == null ? 0 : legacy.schemaVersion(), null);
        ExportFormat.DataV5 data = legacy.data();
        return new ExportFormat.FullExport(legacy.format(), ExportFormat.VERSION, legacy.exportedAt(), legacy.applicationVersion(),
                legacy.schemaVersion(), new ExportFormat.Data(data.contests(), data.mottoShows(), data.candidates(), data.participants(),
                        data.contestParticipations(), data.participantAliases(), data.contestEntries(), data.ballotSnapshots(),
                        data.ballotSnapshotItems(), data.receivedScores(), List.of(), List.of()));
    }

    private static boolean validAssessmentPair(Integer assessment, Integer confidence) {
        return (assessment == null && confidence == null)
                || (assessment != null && confidence != null && assessment >= 1 && assessment <= 5 && confidence >= 1 && confidence <= 5);
    }

    private static Integer migratedAssessment(boolean listened) { return listened ? 3 : null; }
    private static Integer migratedConfidence(boolean listened, boolean relisten) { return !listened ? null : relisten ? 1 : 2; }
    private static <T> Set<Long> uniquePositive(List<T> values, ToLongFunction<T> id, String entity) {
        Set<Long> ids = new HashSet<>();
        for (T value : values) if (value == null || id.applyAsLong(value) < 1 || !ids.add(id.applyAsLong(value))) {
            throw invalid("Die IDs für " + entity + " müssen positiv und eindeutig sein.");
        }
        return ids;
    }
    private static void requireReference(Set<Long> ids, long value, String message) { if (!ids.contains(value)) throw invalid(message); }
    private static void requireText(String value, String message) { if (value == null || value.isBlank()) throw invalid(message); }
    private static void requireUtcInstant(String value, String message) {
        try { if (value == null || !value.endsWith("Z")) throw new IllegalArgumentException(); Instant.parse(value); }
        catch (RuntimeException exception) { throw invalid(message); }
    }
    private static <E extends Enum<E>> boolean enumValue(Class<E> type, String value) {
        try { return value != null && Enum.valueOf(type, value) != null; } catch (IllegalArgumentException exception) { return false; }
    }
    private static String key(long first, long second) { return first + ":" + second; }
    private static BackupFileException invalid(String message) { return new BackupFileException("EXPORT_INVALID", message); }
    private static int schemaVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM databasechangelog")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
    private static List<ExportFormat.Contest> contests(Connection connection) throws SQLException {
        return query(connection, "SELECT id,name,display_order,is_current,created_at,updated_at FROM contest ORDER BY id",
                r -> new ExportFormat.Contest(r.getLong(1), r.getString(2), r.getInt(3), r.getBoolean(4), r.getString(5), r.getString(6)));
    }
    private static List<ExportFormat.MottoShow> mottoShows(Connection connection) throws SQLException {
        return query(connection, "SELECT id,contest_id,show_number,name,entry_list_complete,selected_candidate_id,ballot_closed_at,results_closed_at,final_place,final_place_tied,official_total_points,created_at,updated_at FROM motto_show ORDER BY id",
                r -> new ExportFormat.MottoShow(r.getLong(1), r.getLong(2), r.getInt(3), r.getString(4), r.getBoolean(5), nullableLong(r, 6),
                        r.getString(7), r.getString(8), nullableInt(r, 9), r.getBoolean(10), nullableInt(r, 11), r.getString(12), r.getString(13)));
    }
    private static List<ExportFormat.Candidate> candidates(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,artist,title,youtube_url,comment,status,manual_position,created_at,updated_at FROM candidate ORDER BY id",
                r -> new ExportFormat.Candidate(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), r.getInt(8), r.getString(9), r.getString(10)));
    }
    private static List<ExportFormat.Participant> participants(Connection connection) throws SQLException {
        return query(connection, "SELECT id,display_name,active,created_at,updated_at FROM participant ORDER BY id",
                r -> new ExportFormat.Participant(r.getLong(1), r.getString(2), r.getBoolean(3), r.getString(4), r.getString(5)));
    }
    private static List<ExportFormat.ContestParticipation> contestParticipations(Connection connection) throws SQLException {
        return query(connection, "SELECT id,contest_id,participant_id,country_code,active,created_at,updated_at FROM contest_participation ORDER BY id",
                r -> new ExportFormat.ContestParticipation(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getBoolean(5), r.getString(6), r.getString(7)));
    }
    private static List<ExportFormat.ParticipantAlias> participantAliases(Connection connection) throws SQLException {
        return query(connection, "SELECT id,participant_id,alias FROM participant_alias ORDER BY id",
                r -> new ExportFormat.ParticipantAlias(r.getLong(1), r.getLong(2), r.getString(3)));
    }
    private static List<ExportFormat.ContestEntry> contestEntries(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,contest_id,artist,title,youtube_url,comment,assessment,assessment_confidence,pool_position,ranking_position,contest_participation_id,created_at,updated_at FROM contest_entry ORDER BY id",
                r -> new ExportFormat.ContestEntry(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5), r.getString(6), r.getString(7), nullableInt(r, 8), nullableInt(r, 9), r.getInt(10), nullableInt(r, 11), nullableLong(r, 12), r.getString(13), r.getString(14)));
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
        return query(connection, "SELECT id,motto_show_id,contest_id,contest_participation_id,status,points,created_at,updated_at FROM received_score ORDER BY id",
                r -> new ExportFormat.ReceivedScore(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getString(5), nullableInt(r, 6), r.getString(7), r.getString(8)));
    }
    private static List<ExportFormat.PublishedBallot> publishedBallots(Connection connection) throws SQLException {
        return query(connection, "SELECT id,motto_show_id,contest_id,contest_participation_id,status,created_at,updated_at FROM published_ballot ORDER BY id",
                r -> new ExportFormat.PublishedBallot(r.getLong(1), r.getLong(2), r.getLong(3), r.getLong(4), r.getString(5), r.getString(6), r.getString(7)));
    }
    private static List<ExportFormat.PublishedBallotPosition> publishedBallotPositions(Connection connection) throws SQLException {
        return query(connection, "SELECT id,published_ballot_id,contest_entry_id,rank FROM published_ballot_position ORDER BY id",
                r -> new ExportFormat.PublishedBallotPosition(r.getLong(1), r.getLong(2), r.getLong(3), r.getInt(4)));
    }
    private static <T> List<T> query(Connection connection, String sql, SqlRowMapper<T> mapper) throws SQLException {
        List<T> rows = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
            while (result.next()) rows.add(mapper.map(result));
        }
        return rows;
    }
    private static Long nullableLong(ResultSet result, int index) throws SQLException { long value = result.getLong(index); return result.wasNull() ? null : value; }
    private static Integer nullableInt(ResultSet result, int index) throws SQLException { int value = result.getInt(index); return result.wasNull() ? null : value; }
    private String applicationVersion() { return backups.applicationVersion(); }
    @FunctionalInterface private interface SqlRowMapper<T> { T map(ResultSet result) throws SQLException; }
}
