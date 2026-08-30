package de.venomenon.cscxtool.data;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.CscPoints;
import de.venomenon.cscxtool.system.ApplicationStorage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Produces the analysis contract. It deliberately has no restore/import path: the complete export
 * is a different, versioned format and remains the only format accepted by restore operations.
 */
@Service
public class AnalysisExportService {

    static final String FORMAT = "csc-x-tool-analysis";
    static final int FORMAT_VERSION = 1;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ApplicationStorage storage;

    public AnalysisExportService(DataSource dataSource, ObjectMapper objectMapper, ApplicationStorage storage) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
        this.storage = storage;
    }

    public AnalysisExportPreview preview(AnalysisExportRequest request) {
        Snapshot snapshot = snapshot(request);
        return previewOf(snapshot);
    }

    public AnalysisExportResult create(AnalysisExportRequest request) {
        Snapshot snapshot = snapshot(request);
        Instant generatedAt = Instant.now();
        String filename = "analysis-" + FILE_TIMESTAMP.format(generatedAt) + "-" + UUID.randomUUID() + ".zip";
        Path exports = storage.exportsDirectory();
        Path work = null;
        try {
            cleanupPartialArtifacts(exports);
            work = Files.createTempDirectory(exports, ".analysis-writing-");
            LinkedHashMap<String, byte[]> files = render(snapshot, generatedAt);
            Path part = work.resolve(filename + ".part");
            writeZip(part, files);
            publish(part, exports.resolve(filename));
            return new AnalysisExportResult(filename, generatedAt, previewOf(snapshot));
        } catch (IOException exception) {
            throw new BackupStorageException("Der Analyseexport konnte nicht vollstaendig erstellt werden.", exception);
        } finally {
            BackupService.deleteRecursively(work);
        }
    }

    public Path resolveKnownArtifact(String filename) {
        if (filename == null || !filename.matches("analysis-[0-9]{8}T[0-9]{9}Z-[0-9a-f-]{36}\\.zip")) {
            throw new ApiBadRequestException("ANALYSIS_EXPORT_NOT_FOUND", "Das angeforderte Analysepaket wurde nicht gefunden.");
        }
        Path candidate = storage.exportsDirectory().resolve(filename).normalize();
        if (!candidate.getParent().equals(storage.exportsDirectory()) || !Files.isRegularFile(candidate)) {
            throw new ApiBadRequestException("ANALYSIS_EXPORT_NOT_FOUND", "Das angeforderte Analysepaket wurde nicht gefunden.");
        }
        return candidate;
    }

    /** All SQL reads happen in one read transaction before a package is written. */
    @Transactional(readOnly = true)
    Snapshot snapshot(AnalysisExportRequest rawRequest) {
        AnalysisExportRequest request = AnalysisExportRequest.normalized(rawRequest);
        List<DbContest> allContests = jdbc.query("""
                SELECT id,name,display_order,is_current,own_participation_id
                FROM contest ORDER BY display_order,id
                """, (r, n) -> new DbContest(r.getLong(1), r.getString(2), r.getInt(3), r.getBoolean(4), nullableLong(r, 5)));
        Map<Long, DbContest> contestsById = byId(allContests, DbContest::id);
        validateKnown("contest", request.contestIds(), contestsById.keySet());

        List<DbShow> allShows = jdbc.query("""
                SELECT show.id,show.contest_id,show.show_number,show.name,show.entry_list_complete,contest.is_current
                FROM motto_show show JOIN contest ON contest.id=show.contest_id
                ORDER BY contest.display_order,show.show_number,show.id
                """, (r, n) -> new DbShow(r.getLong(1), r.getLong(2), r.getInt(3), r.getString(4), r.getBoolean(5), r.getBoolean(6)));
        Map<Long, DbShow> showsById = byId(allShows, DbShow::id);
        validateKnown("Mottoshow", request.showIds(), showsById.keySet());

        LinkedHashSet<Long> selectedContestIds = new LinkedHashSet<>(request.contestIds());
        LinkedHashSet<Long> selectedShowIds = new LinkedHashSet<>(request.showIds());
        if (selectedContestIds.isEmpty() && selectedShowIds.isEmpty()) {
            allContests.forEach(contest -> selectedContestIds.add(contest.id()));
            allShows.forEach(show -> selectedShowIds.add(show.id()));
        } else {
            allShows.stream().filter(show -> selectedContestIds.contains(show.contestId())).forEach(show -> selectedShowIds.add(show.id()));
            selectedShowIds.stream().map(showsById::get).filter(Objects::nonNull)
                    .forEach(show -> selectedContestIds.add(show.contestId()));
        }
        List<DbContest> contests = allContests.stream().filter(contest -> selectedContestIds.contains(contest.id())).toList();
        List<DbShow> shows = allShows.stream().filter(show -> selectedShowIds.contains(show.id())).toList();

        DbShow candidateShow = null;
        if (request.candidateShowId() != null) {
            candidateShow = showsById.get(request.candidateShowId());
            if (candidateShow == null || !candidateShow.currentContest()) {
                throw new ApiBadRequestException("ANALYSIS_CANDIDATE_SHOW_INVALID",
                        "Kandidaten duerfen nur aus einer aktuellen Mottoshow exportiert werden.");
            }
        }

        Set<Long> includedContestIds = Set.copyOf(selectedContestIds);
        List<DbParticipation> participations = jdbc.query("""
                SELECT participation.id,participation.contest_id,participation.participant_id,participation.country_code,
                       participant.display_name,participation.id=contest.own_participation_id
                FROM contest_participation participation
                JOIN participant ON participant.id=participation.participant_id
                JOIN contest ON contest.id=participation.contest_id
                ORDER BY contest.display_order,participant.display_name COLLATE NOCASE,participant.id,participation.id
                """, (r, n) -> new DbParticipation(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5), r.getBoolean(6)))
                .stream().filter(participation -> includedContestIds.contains(participation.contestId())).toList();
        Map<Long, DbParticipation> participationsById = byId(participations, DbParticipation::id);

        Map<Long, List<String>> aliasesByParticipant = aliasesByParticipant(participations.stream().map(DbParticipation::participantId).collect(java.util.stream.Collectors.toSet()));
        List<DbParticipant> participants = participations.stream()
                .collect(java.util.stream.Collectors.toMap(DbParticipation::participantId,
                        participation -> new DbParticipant(participation.participantId(), participation.displayName(),
                                aliasesByParticipant.getOrDefault(participation.participantId(), List.of())),
                        (left, right) -> left, LinkedHashMap::new))
                .values().stream().sorted(Comparator.comparing(DbParticipant::displayName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(DbParticipant::id)).toList();

        Set<Long> includedShowIds = Set.copyOf(selectedShowIds);
        List<DbEntry> entries = jdbc.query("""
                SELECT entry.id,entry.contest_id,entry.motto_show_id,entry.artist,entry.title,entry.youtube_url,
                       entry.contest_participation_id,entry.pool_position
                FROM contest_entry entry
                JOIN motto_show show ON show.id=entry.motto_show_id
                JOIN contest ON contest.id=show.contest_id
                ORDER BY contest.display_order,show.show_number,entry.pool_position,entry.id
                """, (r, n) -> new DbEntry(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4), r.getString(5),
                r.getString(6), nullableLong(r, 7), r.getInt(8))).stream().filter(entry -> includedShowIds.contains(entry.showId())).toList();
        Map<Long, List<DbEntry>> entriesByShow = group(entries, DbEntry::showId);

        Map<String, DbBallot> persistedBallots = jdbc.query("""
                SELECT id,motto_show_id,contest_participation_id,status
                FROM published_ballot
                """, (r, n) -> new DbBallot(r.getLong(1), r.getLong(2), r.getLong(3), r.getString(4))).stream()
                .filter(ballot -> includedShowIds.contains(ballot.showId()))
                .collect(java.util.stream.Collectors.toMap(ballot -> ballot.showId() + ":" + ballot.voterParticipationId(), ballot -> ballot));
        Set<Long> ballotIds = persistedBallots.values().stream().map(DbBallot::id).collect(java.util.stream.Collectors.toSet());
        Map<Long, List<DbPosition>> positionsByBallot = positionsByBallot(ballotIds);

        DbShow selectedCandidateShow = candidateShow;
        List<DbCandidate> candidates = selectedCandidateShow == null ? List.of() : jdbc.query("""
                SELECT candidate.id,candidate.motto_show_id,candidate.artist,candidate.title,candidate.youtube_url,candidate.comment,
                       candidate.status,candidate.manual_position,show.selected_candidate_id=candidate.id
                FROM candidate JOIN motto_show show ON show.id=candidate.motto_show_id
                WHERE candidate.motto_show_id=? ORDER BY candidate.manual_position,candidate.id
                """, (r, n) -> new DbCandidate(r.getLong(1), r.getLong(2), r.getString(3), r.getString(4), r.getString(5),
                r.getString(6), r.getString(7), r.getInt(8), r.getBoolean(9)), selectedCandidateShow.id());

        List<DbBallotView> ballots = new ArrayList<>();
        for (DbShow show : shows) {
            List<DbEntry> showEntries = entriesByShow.getOrDefault(show.id(), List.of());
            for (DbParticipation participation : participations) {
                if (participation.contestId() != show.contestId()) continue;
                DbBallot ballot = persistedBallots.get(show.id() + ":" + participation.id());
                String status = ballot == null ? "UNERFASST" : ballot.status();
                List<DbPosition> positions = ballot == null ? List.of() : positionsByBallot.getOrDefault(ballot.id(), List.of());
                Map<Long, DbPosition> positionsByEntry = byId(positions, DbPosition::entryId);
                Long ownEntryId = showEntries.stream().filter(entry -> Objects.equals(entry.submitterParticipationId(), participation.id()))
                        .map(DbEntry::id).findFirst().orElse(null);
                List<Long> outside = "ABGESTIMMT".equals(status) ? showEntries.stream()
                        .filter(entry -> !Objects.equals(entry.submitterParticipationId(), participation.id()))
                        .filter(entry -> !positionsByEntry.containsKey(entry.id())).map(DbEntry::id).toList() : List.of();
                ballots.add(new DbBallotView(show.id(), participation.id(), status, positions, ownEntryId, outside));
            }
        }

        AnalysisScope scope = new AnalysisScope(
                request.contestIds().isEmpty() && request.showIds().isEmpty() ? "FULL_ARCHIVE" : "SELECTED",
                List.copyOf(request.contestIds()), List.copyOf(request.showIds()), request.candidateShowId()
        );
        return new Snapshot(scope, contests, shows, participants, participations, entries, ballots, candidates, selectedCandidateShow, participationsById);
    }

    private LinkedHashMap<String, byte[]> render(Snapshot snapshot, Instant generatedAt) throws IOException {
        AnalysisDocument document = document(snapshot, generatedAt);
        AnalysisExportPreview preview = previewOf(snapshot);
        LinkedHashMap<String, byte[]> files = new LinkedHashMap<>();
        List<String> manifestFiles = new ArrayList<>(List.of("analysis.json", "analysis.md", "participants.csv", "participations.csv", "entries.csv",
                "ballots.csv", "assessment-matrix.csv"));
        if (!snapshot.candidates().isEmpty()) manifestFiles.add("candidates.csv");
        files.put("manifest.json", objectMapper.writeValueAsBytes(new AnalysisManifest(
                FORMAT, FORMAT_VERSION, generatedAt, document.scope(), List.copyOf(manifestFiles), preview
        )));
        files.put("README.md", readme(document));
        files.put("analysis.json", objectMapper.writeValueAsBytes(document));
        files.put("analysis.md", markdown(snapshot, document).getBytes(StandardCharsets.UTF_8));
        files.put("participants.csv", csv(List.of("participant_id", "display_name", "aliases"), snapshot.participants().stream()
                .map(participant -> List.of(text(participant.id()), participant.displayName(), String.join(" | ", participant.aliases()))).toList()));
        files.put("participations.csv", csv(List.of("participation_id", "contest_id", "contest_name", "participant_id", "display_name", "country_code", "is_own_participation"),
                snapshot.participations().stream().map(participation -> List.of(text(participation.id()), text(participation.contestId()),
                        contest(snapshot, participation.contestId()).name(), text(participation.participantId()), participation.displayName(),
                        participation.countryCode(), Boolean.toString(participation.ownParticipation()))).toList()));
        files.put("entries.csv", entriesCsv(snapshot));
        files.put("ballots.csv", ballotsCsv(snapshot));
        files.put("assessment-matrix.csv", assessmentCsv(snapshot));
        if (!snapshot.candidates().isEmpty()) files.put("candidates.csv", candidatesCsv(snapshot));
        return files;
    }

    private AnalysisDocument document(Snapshot snapshot, Instant generatedAt) {
        List<ParticipantDocument> participants = snapshot.participants().stream()
                .map(value -> new ParticipantDocument(value.id(), value.displayName(), value.aliases())).toList();
        List<ContestDocument> contests = snapshot.contests().stream()
                .map(value -> new ContestDocument(value.id(), value.name(), value.displayOrder(), value.current(), value.ownParticipationId())).toList();
        List<ParticipationDocument> participations = snapshot.participations().stream().map(value -> new ParticipationDocument(
                value.id(), value.contestId(), value.participantId(), value.countryCode(), value.ownParticipation()
        )).toList();
        List<ShowDocument> shows = snapshot.shows().stream().map(value -> new ShowDocument(
                value.id(), value.contestId(), value.showNumber(), value.name(), value.entryListComplete()
        )).toList();
        List<EntryDocument> entries = snapshot.entries().stream().map(value -> new EntryDocument(
                value.id(), value.contestId(), value.showId(), value.artist(), value.title(), value.youtubeUrl(), value.submitterParticipationId()
        )).toList();
        List<PublishedBallotDocument> ballots = snapshot.ballots().stream().map(value -> new PublishedBallotDocument(
                value.showId(), value.voterParticipationId(), value.status(), value.positions().stream()
                .map(position -> new BallotPositionDocument(position.rank(), position.entryId(), CscPoints.pointsForRank(position.rank()))).toList(),
                value.ownEntryId(), value.outsideTop15EntryIds()
        )).toList();
        List<CandidateDocument> candidates = snapshot.candidates().stream().map(value -> new CandidateDocument(
                value.id(), value.showId(), snapshot.candidateShow().contestId(), snapshot.candidateShow().showNumber(), snapshot.candidateShow().name(),
                value.artist(), value.title(), value.youtubeUrl(), value.comment(), value.status(), value.manualPosition(), value.selectedAsOwnSubmission()
        )).toList();
        List<AssessmentDocument> assessments = assessments(snapshot);
        return new AnalysisDocument(FORMAT, FORMAT_VERSION, generatedAt, snapshot.scope(), Semantics.contract(),
                new AnalysisData(participants, contests, participations, shows, entries, ballots, candidates), new AnalysisDerived(assessments));
    }

    private byte[] entriesCsv(Snapshot snapshot) {
        List<List<String>> rows = snapshot.entries().stream().map(entry -> {
            DbShow show = show(snapshot, entry.showId());
            DbContest contest = contest(snapshot, entry.contestId());
            DbParticipation submitter = entry.submitterParticipationId() == null ? null : snapshot.participationsById().get(entry.submitterParticipationId());
            return List.<String>of(text(entry.id()), text(contest.id()), contest.name(), text(show.id()), text(show.showNumber()), show.name(), entry.artist(), entry.title(),
                    empty(entry.youtubeUrl()), text(entry.submitterParticipationId()), submitter == null ? "" : submitter.displayName(),
                    submitter == null ? "" : submitter.countryCode());
        }).toList();
        return csv(List.of("entry_id", "contest_id", "contest_name", "show_id", "show_number", "show_name", "artist", "title", "source_or_youtube_url",
                "submitter_participation_id", "submitter_display_name", "submitter_country_code"), rows);
    }

    private byte[] ballotsCsv(Snapshot snapshot) {
        List<List<String>> rows = new ArrayList<>();
        Map<Long, DbEntry> entries = byId(snapshot.entries(), DbEntry::id);
        for (DbBallotView ballot : snapshot.ballots()) {
            if ("ABGESTIMMT".equals(ballot.status())) {
                for (DbPosition position : ballot.positions()) rows.add(ballotRow(snapshot, ballot, "RANKED", position, entries.get(position.entryId())));
            } else rows.add(ballotRow(snapshot, ballot, "NICHT_ABGESTIMMT".equals(ballot.status()) ? "NO_BALLOT" : "UNKNOWN", null, null));
        }
        return csv(ballotHeader(), rows);
    }

    private byte[] assessmentCsv(Snapshot snapshot) {
        List<List<String>> rows = assessments(snapshot).stream().map(value -> {
            DbBallotView ballot = snapshot.ballots().stream().filter(candidate -> candidate.showId() == value.showId()
                    && candidate.voterParticipationId() == value.voterParticipationId()).findFirst().orElseThrow();
            DbEntry entry = snapshot.entries().stream().filter(candidate -> candidate.id() == value.entryId()).findFirst().orElseThrow();
            return ballotRow(snapshot, ballot, value.state(), value.rank() == null ? null : new DbPosition(value.entryId(), value.rank()), entry);
        }).toList();
        return csv(ballotHeader(), rows);
    }

    private List<String> ballotRow(Snapshot snapshot, DbBallotView ballot, String state, DbPosition position, DbEntry entry) {
        DbShow show = show(snapshot, ballot.showId());
        DbContest contest = contest(snapshot, show.contestId());
        DbParticipation voter = snapshot.participationsById().get(ballot.voterParticipationId());
        DbParticipation submitter = entry == null || entry.submitterParticipationId() == null ? null : snapshot.participationsById().get(entry.submitterParticipationId());
        return List.of(text(contest.id()), contest.name(), text(show.id()), text(show.showNumber()), show.name(), text(voter.id()), text(voter.participantId()),
                voter.displayName(), voter.countryCode(), ballot.status(), state, position == null ? "" : text(position.rank()),
                position == null ? ("OUTSIDE_TOP_15".equals(state) ? "0" : "") : text(CscPoints.pointsForRank(position.rank())),
                entry == null ? "" : text(entry.id()), entry == null ? "" : entry.artist(), entry == null ? "" : entry.title(),
                entry == null ? "" : empty(entry.youtubeUrl()), entry == null || entry.submitterParticipationId() == null ? "" : text(entry.submitterParticipationId()),
                submitter == null ? "" : submitter.displayName(), submitter == null ? "" : submitter.countryCode());
    }

    private static List<String> ballotHeader() {
        return List.of("contest_id", "contest_name", "show_id", "show_number", "show_name", "voter_participation_id", "voter_participant_id",
                "voter_display_name", "voter_country_code", "ballot_status", "assessment_state", "rank", "derived_points", "entry_id", "artist",
                "title", "source_or_youtube_url", "submitter_participation_id", "submitter_display_name", "submitter_country_code");
    }

    private byte[] candidatesCsv(Snapshot snapshot) {
        List<List<String>> rows = snapshot.candidates().stream().map(candidate -> List.of(text(candidate.id()), text(candidate.showId()),
                text(snapshot.candidateShow().contestId()), text(snapshot.candidateShow().showNumber()), snapshot.candidateShow().name(), candidate.artist(), candidate.title(),
                empty(candidate.youtubeUrl()), empty(candidate.comment()), candidate.status(), text(candidate.manualPosition()), Boolean.toString(candidate.selectedAsOwnSubmission()))).toList();
        return csv(List.of("candidate_id", "show_id", "contest_id", "show_number", "show_name", "artist", "title", "youtube_url", "comment", "status",
                "manual_position", "selected_as_own_submission"), rows);
    }

    private List<AssessmentDocument> assessments(Snapshot snapshot) {
        Map<Long, List<DbEntry>> entriesByShow = group(snapshot.entries(), DbEntry::showId);
        List<AssessmentDocument> values = new ArrayList<>();
        for (DbBallotView ballot : snapshot.ballots()) {
            Map<Long, DbPosition> positions = byId(ballot.positions(), DbPosition::entryId);
            for (DbEntry entry : entriesByShow.getOrDefault(ballot.showId(), List.of())) {
                DbPosition position = positions.get(entry.id());
                String state = Objects.equals(entry.submitterParticipationId(), ballot.voterParticipationId()) ? "OWN_ENTRY"
                        : position != null ? "RANKED"
                        : "ABGESTIMMT".equals(ballot.status()) ? "OUTSIDE_TOP_15"
                        : "NICHT_ABGESTIMMT".equals(ballot.status()) ? "NO_BALLOT" : "UNKNOWN";
                Integer derivedPoints = position == null
                        ? ("OUTSIDE_TOP_15".equals(state) ? Integer.valueOf(0) : null)
                        : Integer.valueOf(CscPoints.pointsForRank(position.rank()));
                values.add(new AssessmentDocument(ballot.showId(), ballot.voterParticipationId(), entry.id(), state,
                        position == null ? null : position.rank(), derivedPoints));
            }
        }
        return values;
    }

    private String markdown(Snapshot snapshot, AnalysisDocument document) {
        StringBuilder value = new StringBuilder();
        value.append("# CSC X Tool analysis export\n\n");
        value.append("Format `").append(FORMAT).append("` v").append(FORMAT_VERSION).append("; generated at `")
                .append(document.generatedAt()).append("`. This is an analysis contract, not a backup or restore format.\n\n");
        value.append("## Scope\n\n").append(scopeText(document.scope())).append("\n\n");
        value.append("## Interpretation\n\n");
        value.append("- Ranks 1-15 are canonical; points are derived from those ranks.\n");
        value.append("- `OUTSIDE_TOP_15` has derived zero points, but no known rank.\n");
        value.append("- `OWN_ENTRY` is not eligible for that voter and is not a negative rating.\n");
        value.append("- `NO_BALLOT` and `UNKNOWN` are not zero ratings.\n\n");
        for (DbContest contest : snapshot.contests()) {
            value.append("# ").append(markdownText(contest.name())).append("\n\n");
            for (DbShow show : snapshot.shows().stream().filter(candidate -> candidate.contestId() == contest.id()).toList()) {
                appendShowMarkdown(value, snapshot, show);
            }
        }
        appendParticipantHistory(value, snapshot);
        if (!snapshot.candidates().isEmpty()) appendCandidates(value, snapshot);
        return value.toString();
    }

    private void appendShowMarkdown(StringBuilder value, Snapshot snapshot, DbShow show) {
        value.append("## Show ").append(show.showNumber()).append(": ").append(markdownText(show.name())).append("\n\n");
        value.append("Song list complete: **").append(show.entryListComplete() ? "yes" : "no").append("**.\n\n");
        value.append("### Entries\n\n");
        for (DbEntry entry : snapshot.entries().stream().filter(candidate -> candidate.showId() == show.id()).toList()) {
            DbParticipation submitter = entry.submitterParticipationId() == null ? null : snapshot.participationsById().get(entry.submitterParticipationId());
            value.append("- `").append(entry.id()).append("`: ").append(song(entry)).append(" — ")
                    .append(submitter == null ? "unassigned" : markdownText(submitter.displayName()) + " (" + submitter.countryCode() + ")").append("\n");
        }
        value.append("\n### Published ballots\n\n");
        Map<Long, DbEntry> entries = byId(snapshot.entries(), DbEntry::id);
        for (DbBallotView ballot : snapshot.ballots().stream().filter(candidate -> candidate.showId() == show.id()).toList()) {
            DbParticipation voter = snapshot.participationsById().get(ballot.voterParticipationId());
            value.append("#### ").append(markdownText(voter.displayName())).append(" (").append(voter.countryCode()).append(")\n\n");
            if ("ABGESTIMMT".equals(ballot.status())) {
                value.append("Top 15 (rank 1 first):\n\n");
                for (DbPosition position : ballot.positions()) value.append(position.rank()).append(". ").append(song(entries.get(position.entryId())))
                        .append(" — ").append(CscPoints.pointsForRank(position.rank())).append(" points\n");
                value.append("\n");
                if (ballot.ownEntryId() == null) value.append("Own non-votable entry: none.\n\n");
                else value.append("Own non-votable entry: ").append(song(entries.get(ballot.ownEntryId()))).append(". This is not a rating.\n\n");
                value.append("Outside Top 15 (unordered set; no rank is known):\n\n");
                if (ballot.outsideTop15EntryIds().isEmpty()) value.append("- none\n\n");
                else {
                    for (Long entryId : ballot.outsideTop15EntryIds()) value.append("- ").append(song(entries.get(entryId))).append("\n");
                    value.append("\n");
                }
            } else if ("NICHT_ABGESTIMMT".equals(ballot.status())) {
                value.append("Status: **NO_BALLOT**. No preference or zero rating can be inferred.\n\n");
            } else value.append("Status: **UNKNOWN**. The source has not been recorded; no preference or zero rating can be inferred.\n\n");
        }
    }

    private void appendParticipantHistory(StringBuilder value, Snapshot snapshot) {
        value.append("# Participant history\n\n");
        Map<Long, DbEntry> entries = byId(snapshot.entries(), DbEntry::id);
        for (DbParticipant participant : snapshot.participants()) {
            value.append("## ").append(markdownText(participant.displayName())).append("\n\n");
            if (!participant.aliases().isEmpty()) value.append("Aliases: ").append(markdownText(String.join(", ", participant.aliases()))).append("\n\n");
            List<DbParticipation> participations = snapshot.participations().stream().filter(valueParticipation -> valueParticipation.participantId() == participant.id()).toList();
            value.append("Participations: ").append(participations.stream().map(participation -> markdownText(contest(snapshot, participation.contestId()).name())
                    + " (" + participation.countryCode() + ")").collect(java.util.stream.Collectors.joining(", "))).append("\n\n");
            value.append("Historical entries:\n\n");
            List<DbEntry> submitted = snapshot.entries().stream().filter(entry -> participations.stream().anyMatch(participation -> participation.id() == entry.submitterParticipationId())).toList();
            if (submitted.isEmpty()) value.append("- none in this scope\n\n");
            else {
                for (DbEntry entry : submitted) {
                    DbShow show = show(snapshot, entry.showId());
                    value.append("- ").append(markdownText(contest(snapshot, entry.contestId()).name())).append(", show ").append(show.showNumber())
                            .append(": ").append(song(entry)).append("\n");
                }
                value.append("\n");
            }
            value.append("Published Top 15:\n\n");
            List<DbBallotView> voted = snapshot.ballots().stream().filter(ballot -> participations.stream()
                    .anyMatch(participation -> participation.id() == ballot.voterParticipationId()) && "ABGESTIMMT".equals(ballot.status())).toList();
            if (voted.isEmpty()) value.append("- none in this scope\n\n");
            else {
                for (DbBallotView ballot : voted) {
                    DbShow show = show(snapshot, ballot.showId());
                    value.append("- ").append(markdownText(contest(snapshot, show.contestId()).name())).append(", show ").append(show.showNumber()).append(": ")
                            .append(ballot.positions().stream().map(position -> position.rank() + ". " + song(entries.get(position.entryId())))
                                    .collect(java.util.stream.Collectors.joining("; "))).append("\n");
                }
                value.append("\n");
            }
        }
    }

    private void appendCandidates(StringBuilder value, Snapshot snapshot) {
        value.append("# Prediction candidates (separate from historic entries)\n\n");
        value.append("These are current candidates for ").append(markdownText(snapshot.candidateShow().name())).append(" (show ")
                .append(snapshot.candidateShow().showNumber()).append("). They are not historical contest entries and contain no prediction.\n\n");
        for (DbCandidate candidate : snapshot.candidates()) {
            value.append("- `").append(candidate.id()).append("`: ").append(markdownText(candidate.artist())).append(" — ").append(markdownText(candidate.title()))
                    .append("; status: ").append(markdownText(candidate.status())).append("; own submission: ").append(candidate.selectedAsOwnSubmission() ? "yes" : "no");
            if (candidate.comment() != null && !candidate.comment().isBlank()) value.append("; comment: ").append(markdownText(candidate.comment()));
            value.append("\n");
        }
        value.append("\n");
    }

    private static byte[] readme(AnalysisDocument document) {
        String value = "# CSC X Tool analysis export\n\n"
                + "`analysis.json` is the canonical machine-readable source of this package. Its format is `" + FORMAT + "` version " + FORMAT_VERSION + ".\n\n"
                + "This package is not a backup, restore input, database copy, official result table, exclusion list, AI request or cloud upload.\n\n"
                + "CSV files use UTF-8 with BOM, semicolons and CRLF line endings. `assessment-matrix.csv` deliberately keeps `RANKED`, `OUTSIDE_TOP_15`, `OWN_ENTRY`, `NO_BALLOT` and `UNKNOWN` separate.\n\n"
                + "`OUTSIDE_TOP_15` has zero derived points but no known rank. `OWN_ENTRY`, `NO_BALLOT` and `UNKNOWN` have neither a rank nor a zero rating.\n\n"
                + "Scope: " + scopeText(document.scope()) + "\n";
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String scopeText(AnalysisScope scope) {
        if ("FULL_ARCHIVE".equals(scope.mode())) return "Complete archive" + (scope.candidateShowId() == null ? "." : "; plus selected current candidates.");
        return "Selected contest IDs: " + scope.contestIds() + "; selected show IDs: " + scope.showIds()
                + (scope.candidateShowId() == null ? "." : "; current candidate show ID: " + scope.candidateShowId() + ".");
    }

    private AnalysisExportPreview previewOf(Snapshot snapshot) {
        long voted = snapshot.ballots().stream().filter(ballot -> "ABGESTIMMT".equals(ballot.status())).count();
        long noBallot = snapshot.ballots().stream().filter(ballot -> "NICHT_ABGESTIMMT".equals(ballot.status())).count();
        long unknown = snapshot.ballots().stream().filter(ballot -> "UNERFASST".equals(ballot.status())).count();
        return new AnalysisExportPreview(snapshot.scope(), snapshot.participants().size(), snapshot.participations().size(), snapshot.shows().size(),
                snapshot.entries().size(), voted, noBallot, unknown, snapshot.candidates().size(), assessments(snapshot).size());
    }

    private Map<Long, List<String>> aliasesByParticipant(Set<Long> participantIds) {
        if (participantIds.isEmpty()) return Map.of();
        Map<Long, List<String>> aliases = new HashMap<>();
        jdbc.query("SELECT participant_id,alias FROM participant_alias ORDER BY participant_id,alias COLLATE NOCASE,id", (r, n) -> {
            long participantId = r.getLong(1);
            if (participantIds.contains(participantId)) aliases.computeIfAbsent(participantId, ignored -> new ArrayList<>()).add(r.getString(2));
            return null;
        });
        aliases.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(aliases);
    }

    private Map<Long, List<DbPosition>> positionsByBallot(Set<Long> ballotIds) {
        if (ballotIds.isEmpty()) return Map.of();
        Map<Long, List<DbPosition>> positions = new HashMap<>();
        jdbc.query("SELECT published_ballot_id,contest_entry_id,rank FROM published_ballot_position ORDER BY published_ballot_id,rank", (r, n) -> {
            long ballotId = r.getLong(1);
            if (ballotIds.contains(ballotId)) positions.computeIfAbsent(ballotId, ignored -> new ArrayList<>())
                    .add(new DbPosition(r.getLong(2), r.getInt(3)));
            return null;
        });
        positions.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(positions);
    }

    private static <T> Map<Long, T> byId(List<T> values, java.util.function.ToLongFunction<T> id) {
        Map<Long, T> result = new LinkedHashMap<>();
        for (T value : values) result.put(id.applyAsLong(value), value);
        return result;
    }

    private static <T> Map<Long, List<T>> group(List<T> values, java.util.function.ToLongFunction<T> key) {
        Map<Long, List<T>> result = new LinkedHashMap<>();
        for (T value : values) result.computeIfAbsent(key.applyAsLong(value), ignored -> new ArrayList<>()).add(value);
        result.replaceAll((ignored, value) -> List.copyOf(value));
        return result;
    }

    private static void validateKnown(String kind, List<Long> requested, Set<Long> known) {
        for (Long id : requested) if (!known.contains(id)) {
            throw new ApiBadRequestException("ANALYSIS_SCOPE_INVALID", "Die ausgewaehlte " + kind + " existiert nicht mehr.");
        }
    }

    private static DbContest contest(Snapshot snapshot, long id) {
        return snapshot.contests().stream().filter(value -> value.id() == id).findFirst().orElseThrow();
    }

    private static DbShow show(Snapshot snapshot, long id) {
        return snapshot.shows().stream().filter(value -> value.id() == id).findFirst().orElseThrow();
    }

    private static void writeZip(Path target, LinkedHashMap<String, byte[]> files) throws IOException {
        try (OutputStream output = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0L);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
    }

    private static void publish(Path part, Path target) throws IOException {
        try {
            Files.move(part, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(part, target);
        }
    }

    private static void cleanupPartialArtifacts(Path directory) {
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().contains(".part") || path.getFileName().toString().startsWith(".analysis-writing-"))
                    .forEach(BackupService::deleteRecursively);
        } catch (IOException ignored) {
            // The following write reports the concrete storage failure to the caller.
        }
    }

    private static byte[] csv(List<String> header, List<List<String>> rows) {
        StringBuilder value = new StringBuilder("\uFEFF");
        appendCsv(value, header);
        for (List<String> row : rows) appendCsv(value, row);
        return value.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsv(StringBuilder destination, List<String> row) {
        for (int index = 0; index < row.size(); index++) {
            if (index > 0) destination.append(';');
            String cell = row.get(index) == null ? "" : row.get(index);
            boolean quoted = cell.indexOf(';') >= 0 || cell.indexOf('"') >= 0 || cell.indexOf('\r') >= 0 || cell.indexOf('\n') >= 0;
            if (quoted) destination.append('"');
            destination.append(cell.replace("\"", "\"\""));
            if (quoted) destination.append('"');
        }
        destination.append("\r\n");
    }

    private static String song(DbEntry entry) {
        return markdownText(entry.artist()) + " - " + markdownText(entry.title());
    }

    private static String markdownText(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("|", "\\|").replace("\r", " ").replace("\n", "<br>");
    }

    private static String text(long value) { return Long.toString(value); }
    private static String text(int value) { return Integer.toString(value); }
    private static String text(Integer value) { return value == null ? "" : Integer.toString(value); }
    private static String text(Long value) { return value == null ? "" : Long.toString(value); }
    private static String empty(String value) { return value == null ? "" : value; }
    private static Long nullableLong(java.sql.ResultSet result, int index) throws java.sql.SQLException {
        long value = result.getLong(index);
        return result.wasNull() ? null : value;
    }

    record AnalysisExportRequest(List<Long> contestIds, List<Long> showIds, Long candidateShowId) {
        static AnalysisExportRequest normalized(AnalysisExportRequest request) {
            if (request == null) return new AnalysisExportRequest(List.of(), List.of(), null);
            return new AnalysisExportRequest(ids(request.contestIds()), ids(request.showIds()), request.candidateShowId());
        }
        private static List<Long> ids(List<Long> values) {
            if (values == null) return List.of();
            LinkedHashSet<Long> normalized = new LinkedHashSet<>();
            for (Long value : values) {
                if (value == null || value <= 0) throw new ApiBadRequestException("ANALYSIS_SCOPE_INVALID", "Export-IDs muessen positive Zahlen sein.");
                normalized.add(value);
            }
            return List.copyOf(normalized);
        }
    }

    record AnalysisExportResult(String filename, Instant generatedAt, AnalysisExportPreview preview) { }
    record AnalysisExportPreview(AnalysisScope scope, int participants, int participations, int shows, int entries,
                                 long votedBallots, long noBallots, long unknownBallots, int candidates, int assessments) { }
    record AnalysisScope(String mode, List<Long> contestIds, List<Long> showIds, Long candidateShowId) { }

    record AnalysisManifest(String format, int formatVersion, Instant generatedAt, AnalysisScope scope, List<String> files,
                            AnalysisExportPreview counts) { }
    record AnalysisDocument(String format, int formatVersion, Instant generatedAt, AnalysisScope scope, Semantics semantics,
                            AnalysisData data, AnalysisDerived derived) { }
    record Semantics(boolean rankIsCanonical, boolean pointsDerivedFromRank, String outsideTop15, String ownEntry,
                     String noBallot, String unknown, Map<Integer, Integer> pointsByRank) {
        static Semantics contract() {
            Map<Integer, Integer> points = new LinkedHashMap<>();
            for (int rank = 1; rank <= 15; rank++) points.put(rank, CscPoints.pointsForRank(rank));
            return new Semantics(true, true,
                    "Eligible entry absent from a recorded Top 15; unordered and zero derived points, with no known rank.",
                    "The voter's own non-votable entry; not a rating and has no points.",
                    "The participant did not submit a ballot; no preference or points can be inferred.",
                    "The ballot status has not been recorded; no preference or points can be inferred.", points);
        }
    }
    record AnalysisData(List<ParticipantDocument> participants, List<ContestDocument> contests, List<ParticipationDocument> participations,
                        List<ShowDocument> shows, List<EntryDocument> entries, List<PublishedBallotDocument> publishedBallots,
                        List<CandidateDocument> predictionCandidates) { }
    record AnalysisDerived(List<AssessmentDocument> assessmentMatrix) { }
    record ParticipantDocument(long id, String displayName, List<String> aliases) { }
    record ContestDocument(long id, String name, int displayOrder, boolean current, Long ownParticipationId) { }
    record ParticipationDocument(long id, long contestId, long participantId, String countryCode, boolean ownParticipation) { }
    record ShowDocument(long id, long contestId, int showNumber, String name, boolean entryListComplete) { }
    record EntryDocument(long id, long contestId, long showId, String artist, String title, String youtubeUrl, Long submitterParticipationId) { }
    record PublishedBallotDocument(long showId, long voterParticipationId, String status, List<BallotPositionDocument> positions,
                                   Long ownEntryId, List<Long> outsideTop15EntryIds) { }
    record BallotPositionDocument(int rank, long entryId, int derivedPoints) { }
    record CandidateDocument(long id, long showId, long contestId, int showNumber, String showName, String artist, String title,
                             String youtubeUrl, String comment, String status, int manualPosition, boolean selectedAsOwnSubmission) { }
    record AssessmentDocument(long showId, long voterParticipationId, long entryId, String state, Integer rank, Integer derivedPoints) { }

    record Snapshot(AnalysisScope scope, List<DbContest> contests, List<DbShow> shows, List<DbParticipant> participants,
                    List<DbParticipation> participations, List<DbEntry> entries, List<DbBallotView> ballots, List<DbCandidate> candidates,
                    DbShow candidateShow, Map<Long, DbParticipation> participationsById) { }
    record DbContest(long id, String name, int displayOrder, boolean current, Long ownParticipationId) { }
    record DbShow(long id, long contestId, int showNumber, String name, boolean entryListComplete, boolean currentContest) { }
    record DbParticipant(long id, String displayName, List<String> aliases) { }
    record DbParticipation(long id, long contestId, long participantId, String countryCode, String displayName, boolean ownParticipation) { }
    record DbEntry(long id, long contestId, long showId, String artist, String title, String youtubeUrl, Long submitterParticipationId, int poolPosition) { }
    record DbBallot(long id, long showId, long voterParticipationId, String status) { }
    record DbPosition(long entryId, int rank) { }
    record DbBallotView(long showId, long voterParticipationId, String status, List<DbPosition> positions, Long ownEntryId,
                        List<Long> outsideTop15EntryIds) { }
    record DbCandidate(long id, long showId, String artist, String title, String youtubeUrl, String comment, String status, int manualPosition,
                       boolean selectedAsOwnSubmission) { }
}
