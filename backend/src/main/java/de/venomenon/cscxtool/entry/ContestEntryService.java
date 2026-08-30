package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.contest.ContestRepository;
import de.venomenon.cscxtool.participant.ParticipantNotFoundException;
import de.venomenon.cscxtool.show.ShowContext;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
class ContestEntryService {

    private final ContestEntryRepository repository;
    private final ClipboardEntryParser clipboardEntryParser;
    private final YoutubeUrlNormalizer youtubeUrlNormalizer;
    private final ContestRepository contests;
    private final HistoricalEntryImportParser historicalEntryImportParser;

    ContestEntryService(
            ContestEntryRepository repository,
            ClipboardEntryParser clipboardEntryParser,
            YoutubeUrlNormalizer youtubeUrlNormalizer,
            ContestRepository contests,
            HistoricalEntryImportParser historicalEntryImportParser
    ) {
        this.repository = repository;
        this.clipboardEntryParser = clipboardEntryParser;
        this.youtubeUrlNormalizer = youtubeUrlNormalizer;
        this.contests = contests;
        this.historicalEntryImportParser = historicalEntryImportParser;
    }

    List<ContestEntry> findAll(long showId) {
        requireShowContext(showId);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    List<ContestEntry> reorder(long showId, ReorderContestEntriesRequest request) {
        requireCurrentShow(showId);
        if (request == null || request.entryIds() == null) {
            throw poolReorderConflict();
        }
        List<Long> submittedIds = request.entryIds();
        Set<Long> currentIds = repository.findAllByShowId(showId).stream().map(ContestEntry::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> uniqueSubmittedIds = new HashSet<>(submittedIds);
        if (currentIds.size() != submittedIds.size() || uniqueSubmittedIds.size() != submittedIds.size()
                || !currentIds.equals(uniqueSubmittedIds)) {
            throw poolReorderConflict();
        }
        repository.replacePool(showId, submittedIds);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    ContestEntry create(long showId, CreateContestEntryRequest request) {
        ShowContext context = requireShowContext(showId);
        String artist = requiredText(request.artist(), "Der Interpret darf nicht leer sein.");
        String title = requiredText(request.title(), "Der Titel darf nicht leer sein.");
        if (context.currentContest()) {
            if (request.participantId() != null) throw currentEntryParticipantAssignmentForbidden();
            return repository.create(showId, artist, title, requiredYoutubeUrl(request.youtubeUrl()), optionalText(request.comment()));
        }
        requireHistoricalListOpen(context);
        long participationId = requiredParticipationForShow(showId, request.participantId());
        return repository.create(showId, artist, title, optionalHistoricalUrl(request.youtubeUrl()), optionalText(request.comment()), participationId);
    }

    @Transactional
    ContestEntry update(long showId, long entryId, UpdateContestEntryRequest request) {
        ShowContext context = requireShowContext(showId);
        String artist = requiredText(request.artist(), "Der Interpret darf nicht leer sein.");
        String title = requiredText(request.title(), "Der Titel darf nicht leer sein.");
        boolean updated;
        if (context.currentContest()) {
            if (request.participantId() != null) throw currentEntryParticipantAssignmentForbidden();
            updated = repository.update(entryId, showId, artist, title, requiredYoutubeUrl(request.youtubeUrl()), optionalText(request.comment()));
        } else {
            requireHistoricalListOpen(context);
            updated = repository.updateHistorical(entryId, showId, artist, title, optionalHistoricalUrl(request.youtubeUrl()),
                    optionalText(request.comment()), requiredParticipationForShow(showId, request.participantId()));
        }
        if (!updated) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    ContestEntry updateAssessment(long showId, long entryId, UpdateContestEntryAssessmentRequest request) {
        requireCurrentShow(showId);
        if (request == null || !isValidAssessmentPair(request.assessment(), request.assessmentConfidence())) {
            throw new ApiBadRequestException(
                    "INVALID_ENTRY_ASSESSMENT",
                    "Einschätzung und Sicherheit müssen gemeinsam leer sein oder jeweils zwischen 1 und 5 liegen."
            );
        }
        if (!repository.updateAssessment(entryId, showId, request.assessment(), request.assessmentConfidence())) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    ContestEntry updateParticipantAssignment(long showId, long entryId, UpdateParticipantAssignmentRequest request) {
        requireCurrentShow(showId);
        if (!repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "PARTICIPANT_ASSIGNMENT_REQUIRES_CLOSED_BALLOT",
                    "Teilnehmer können erst nach dem Abschluss der eigenen Top 15 zugeordnet werden."
            );
        }
        ContestEntry entry = repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
        Long participantId = request == null ? null : request.participantId();
        Long participationId = null;
        if (participantId != null) {
            if (!repository.participantExists(participantId)) {
                throw new ParticipantNotFoundException(participantId);
            }
            var participation = contests.findParticipationForShow(showId, participantId).orElseThrow(() -> new ApiConflictException(
                    "PARTICIPANT_NOT_IN_CONTEST",
                    "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil."
            ));
            if (!participation.active() && !participantId.equals(entry.participantId())) {
                throw new ApiConflictException(
                        "INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED",
                        "Inaktive Teilnehmer können nicht neu einem Wettbewerbsbeitrag zugeordnet werden."
                );
            }
            repository.findEntryIdByParticipation(showId, participation.id())
                    .filter(assignedEntryId -> assignedEntryId != entry.id())
                    .ifPresent(assignedEntryId -> {
                        throw duplicateParticipantAssignment();
                    });
            participationId = participation.id();
        }
        try {
            if (!repository.updateParticipantAssignment(entryId, showId, participationId)) {
                throw new ContestEntryNotFoundException(entryId, showId);
            }
        } catch (DataIntegrityViolationException exception) {
            if (isParticipantAssignmentUniqueConstraint(exception)) {
                throw duplicateParticipantAssignment();
            }
            throw exception;
        }
        return repository.findByIdAndShowId(entryId, showId).orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    void delete(long showId, long entryId) {
        ShowContext context = requireShowContext(showId);
        if (!context.currentContest()) requireHistoricalListOpen(context);
        ContestEntry entry = repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
        if (entry.rankingPosition() != null && repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "BALLOT_REOPEN_REQUIRED",
                    "Die abgeschlossene Abstimmung muss vor einer Rang\u00e4nderung bewusst wieder ge\u00f6ffnet werden."
            );
        }
        if (!repository.delete(entryId, showId)) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        repository.replacePool(showId, repository.findPoolEntryIds(showId));
        if (entry.rankingPosition() != null) {
            repository.replaceRanking(showId, repository.findRankedEntryIds(showId));
        }
    }

    List<ImportPreviewLine> preview(long showId, ImportPreviewRequest request) {
        requireCurrentShow(showId);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        return markPossibleDuplicates(clipboardEntryParser.parse(html, text), repository.findAllByShowId(showId));
    }

    @Transactional
    List<ContestEntry> importEntries(long showId, ImportContestEntriesRequest request) {
        requireCurrentShow(showId);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ApiBadRequestException("EMPTY_IMPORT", "W\u00e4hle mindestens einen vollst\u00e4ndigen Beitrag f\u00fcr den Import aus.");
        }

        List<ValidatedImportEntry> validatedEntries = request.entries().stream().map(this::validateImportEntry).toList();
        for (ValidatedImportEntry entry : validatedEntries) {
            repository.create(showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment());
        }
        return repository.findAllByShowId(showId);
    }

    List<HistoricalImportPreviewLine> previewHistorical(long showId, ImportPreviewRequest request) {
        ShowContext context = requireShowContext(showId);
        requireHistoricalListOpen(context);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        return markHistoricalPossibleDuplicates(
                historicalEntryImportParser.parse(html, text, repository.findHistoricalImportParticipants(showId)),
                repository.findAllByShowId(showId)
        );
    }

    @Transactional
    List<ContestEntry> importHistoricalEntries(long showId, HistoricalImportEntriesRequest request) {
        ShowContext context = requireShowContext(showId);
        requireHistoricalListOpen(context);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ApiBadRequestException("EMPTY_IMPORT", "Wähle mindestens einen vollständigen Beitrag für den Import aus.");
        }
        List<ValidatedHistoricalImportEntry> entries = request.entries().stream()
                .map(entry -> validateHistoricalImportEntry(showId, entry)).toList();
        Set<Long> participantIds = new HashSet<>();
        for (ValidatedHistoricalImportEntry entry : entries) {
            if (!participantIds.add(entry.participantId())) throw duplicateParticipantAssignment();
            Optional<ContestEntry> existing = repository.findEntryIdByParticipation(showId, entry.participationId())
                    .flatMap(id -> repository.findByIdAndShowId(id, showId));
            if (entry.replaceEntryId() == null && existing.isPresent()) throw duplicateParticipantAssignment();
            if (entry.replaceEntryId() != null && (existing.isEmpty() || existing.get().id() != entry.replaceEntryId())) {
                throw new ApiConflictException(
                        "HISTORICAL_IMPORT_REPLACEMENT_CONFLICT",
                        "Ein Ersatzimport muss den bereits zugeordneten Beitrag desselben Teilnehmers ausdrücklich auswählen."
                );
            }
        }
        for (ValidatedHistoricalImportEntry entry : entries) {
            if (entry.replaceEntryId() == null) {
                repository.create(showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment(), entry.participationId());
            } else if (!repository.updateHistorical(entry.replaceEntryId(), showId, entry.artist(), entry.title(), entry.youtubeUrl(),
                    entry.comment(), entry.participationId())) {
                throw new ApiConflictException("HISTORICAL_IMPORT_REPLACEMENT_CONFLICT", "Der zu ersetzende Beitrag wurde nicht gefunden.");
            }
        }
        return repository.findAllByShowId(showId);
    }

    @Transactional
    void completeHistoricalEntryList(long showId) {
        ShowContext context = requireShowContext(showId);
        requireHistorical(context);
        if (repository.historicalEntryCount(showId) == 0) {
            throw new ApiConflictException("ENTRY_LIST_EMPTY", "Eine vollständige Songliste muss mindestens einen Beitrag enthalten.");
        }
        if (repository.unassignedHistoricalEntryCount(showId) > 0) {
            throw new ApiConflictException("ENTRY_LIST_HAS_UNASSIGNED_ENTRIES", "Alle Beiträge der Songliste benötigen eine gültige Contest-Teilnahme.");
        }
        repository.setEntryListComplete(showId, true);
    }

    @Transactional
    void reopenHistoricalEntryList(long showId) {
        ShowContext context = requireShowContext(showId);
        requireHistorical(context);
        repository.setEntryListComplete(showId, false);
    }

    private List<HistoricalImportPreviewLine> markHistoricalPossibleDuplicates(
            List<HistoricalImportPreviewLine> lines, List<ContestEntry> existingEntries
    ) {
        Set<Long> assignedParticipants = existingEntries.stream().map(ContestEntry::participantId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Set<String> existingArtistTitle = existingEntries.stream().map(entry -> artistTitleKey(entry.artist(), entry.title()))
                .collect(java.util.stream.Collectors.toSet());
        Set<Long> seenParticipants = new HashSet<>();
        return lines.stream().map(line -> line.participantId() != null
                && (!seenParticipants.add(line.participantId()) || assignedParticipants.contains(line.participantId())
                || (line.artist() != null && line.title() != null && existingArtistTitle.contains(artistTitleKey(line.artist(), line.title()))))
                ? line.withPossibleDuplicate() : line).toList();
    }

    private List<ImportPreviewLine> markPossibleDuplicates(List<ImportPreviewLine> lines, List<ContestEntry> existingEntries) {
        Set<String> existingYoutubeUrls = new HashSet<>();
        Set<String> existingArtistTitle = new HashSet<>();
        for (ContestEntry entry : existingEntries) {
            existingYoutubeUrls.add(normalized(entry.youtubeUrl()));
            existingArtistTitle.add(artistTitleKey(entry.artist(), entry.title()));
        }

        Map<String, Integer> youtubeCounts = new HashMap<>();
        Map<String, Integer> artistTitleCounts = new HashMap<>();
        for (ImportPreviewLine line : lines) {
            if (isSupportedPreviewYoutubeUrl(line.youtubeUrl())) {
                youtubeCounts.merge(normalized(line.youtubeUrl()), 1, Integer::sum);
            }
            if (line.artist() != null && line.title() != null) {
                artistTitleCounts.merge(artistTitleKey(line.artist(), line.title()), 1, Integer::sum);
            }
        }

        return lines.stream().map(line -> {
            boolean duplicate = (isSupportedPreviewYoutubeUrl(line.youtubeUrl())
                    && (existingYoutubeUrls.contains(normalized(line.youtubeUrl()))
                    || youtubeCounts.getOrDefault(normalized(line.youtubeUrl()), 0) > 1))
                    || (line.artist() != null && line.title() != null
                    && (existingArtistTitle.contains(artistTitleKey(line.artist(), line.title()))
                    || artistTitleCounts.getOrDefault(artistTitleKey(line.artist(), line.title()), 0) > 1));
            return duplicate ? line.withPossibleDuplicate() : line;
        }).toList();
    }

    private boolean isSupportedPreviewYoutubeUrl(String value) {
        if (value == null) {
            return false;
        }
        try {
            return youtubeUrlNormalizer.normalize(value).equals(value);
        } catch (ApiBadRequestException exception) {
            return false;
        }
    }

    private ValidatedImportEntry validateImportEntry(ImportContestEntryRequest entry) {
        if (entry == null) {
            throw new ApiBadRequestException("INVALID_IMPORT_ENTRY", "Ein ausgew\u00e4hlter Importbeitrag ist ung\u00fcltig.");
        }
        return new ValidatedImportEntry(
                requiredText(entry.artist(), "Der Interpret eines Importbeitrags darf nicht leer sein."),
                requiredText(entry.title(), "Der Titel eines Importbeitrags darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(entry.youtubeUrl()),
                optionalText(entry.comment())
        );
    }

    private ShowContext requireShowContext(long showId) {
        return repository.findShowContext(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    private void requireCurrentShow(long showId) {
        if (!requireShowContext(showId).currentContest()) {
            throw new ApiConflictException("CURRENT_SHOW_REQUIRED", "Diese Funktion gehört zum aktuellen CSC-X-Workflow und ist für Archivshows nicht verfügbar.");
        }
    }

    private static void requireHistorical(ShowContext context) {
        if (context.currentContest()) {
            throw new ApiConflictException("HISTORICAL_SHOW_REQUIRED", "Diese Funktion ist ausschließlich für historische CSC-Ausgaben verfügbar.");
        }
    }

    private static void requireHistoricalListOpen(ShowContext context) {
        requireHistorical(context);
        if (context.entryListComplete()) {
            throw new ApiConflictException("ENTRY_LIST_REOPEN_REQUIRED", "Die vollständige Songliste muss vor einer Korrektur bewusst wieder geöffnet werden.");
        }
    }

    private long requiredParticipationForShow(long showId, Long participantId) {
        if (participantId == null) {
            throw new ApiBadRequestException("HISTORICAL_ENTRY_PARTICIPANT_REQUIRED", "Historische Beiträge benötigen einen Einreichenden aus dem Teilnehmerfeld.");
        }
        if (!repository.participantExists(participantId)) throw new ParticipantNotFoundException(participantId);
        return contests.findParticipationForShow(showId, participantId).orElseThrow(() -> new ApiConflictException(
                "PARTICIPANT_NOT_IN_CONTEST", "Der Teilnehmer nimmt nicht an der CSC-Ausgabe dieser Mottoshow teil."
        )).id();
    }

    private ValidatedHistoricalImportEntry validateHistoricalImportEntry(long showId, HistoricalImportEntryRequest entry) {
        if (entry == null) throw new ApiBadRequestException("INVALID_IMPORT_ENTRY", "Ein ausgewählter Importbeitrag ist ungültig.");
        long participantId = entry.participantId() == null ? -1 : entry.participantId();
        long participationId = requiredParticipationForShow(showId, entry.participantId());
        return new ValidatedHistoricalImportEntry(
                requiredText(entry.artist(), "Der Interpret eines Importbeitrags darf nicht leer sein."),
                requiredText(entry.title(), "Der Titel eines Importbeitrags darf nicht leer sein."), optionalHistoricalUrl(entry.youtubeUrl()),
                optionalText(entry.comment()), participantId, participationId, entry.replaceEntryId()
        );
    }

    private String requiredYoutubeUrl(String value) {
        return youtubeUrlNormalizer.normalize(requiredText(value, "Der YouTube-Link darf nicht leer sein."));
    }

    private static String optionalHistoricalUrl(String value) {
        String normalized = optionalText(value);
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException();
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new ApiBadRequestException("INVALID_HISTORICAL_SOURCE_URL", "Der optionale Quelllink muss eine HTTP- oder HTTPS-Adresse sein.");
        }
    }

    private static ApiConflictException duplicateParticipantAssignment() {
        return new ApiConflictException(
                "PARTICIPANT_ALREADY_ASSIGNED_IN_SHOW",
                "Ein Teilnehmer kann innerhalb derselben Mottoshow nur einem Wettbewerbsbeitrag zugeordnet werden."
        );
    }

    private static ApiConflictException currentEntryParticipantAssignmentForbidden() {
        return new ApiConflictException(
                "CURRENT_ENTRY_PARTICIPANT_ASSIGNMENT_FORBIDDEN",
                "Die Teilnehmerzuordnung der aktuellen Ausgabe erfolgt weiterhin erst nach Abschluss der eigenen Abstimmung."
        );
    }

    private static ApiConflictException poolReorderConflict() {
        return new ApiConflictException(
                "POOL_REORDER_CONFLICT",
                "Die manuelle Reihenfolge muss jeden aktuellen Beitrag dieser Mottoshow genau einmal enthalten."
        );
    }

    private static boolean isParticipantAssignmentUniqueConstraint(Throwable exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("contest_entry.motto_show_id, contest_entry.contest_participation_id")) {
                return true;
            }
        }
        return false;
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ApiBadRequestException("VALIDATION_ERROR", message);
        }
        return value.trim();
    }

    private static String optionalText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String artistTitleKey(String artist, String title) {
        return normalized(artist) + "|" + normalized(title);
    }

    private static boolean isValidAssessmentPair(Integer assessment, Integer assessmentConfidence) {
        if (assessment == null || assessmentConfidence == null) {
            return assessment == null && assessmentConfidence == null;
        }
        return assessment >= 1 && assessment <= 5 && assessmentConfidence >= 1 && assessmentConfidence <= 5;
    }

    private record ValidatedImportEntry(String artist, String title, String youtubeUrl, String comment) {
    }

    private record ValidatedHistoricalImportEntry(
            String artist, String title, String youtubeUrl, String comment, long participantId, long participationId, Long replaceEntryId
    ) { }
}
