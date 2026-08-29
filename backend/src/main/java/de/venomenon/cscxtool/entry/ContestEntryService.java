package de.venomenon.cscxtool.entry;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.participant.ParticipantNotFoundException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

@Service
class ContestEntryService {

    private final ContestEntryRepository repository;
    private final ClipboardEntryParser clipboardEntryParser;
    private final YoutubeUrlNormalizer youtubeUrlNormalizer;

    ContestEntryService(
            ContestEntryRepository repository,
            ClipboardEntryParser clipboardEntryParser,
            YoutubeUrlNormalizer youtubeUrlNormalizer
    ) {
        this.repository = repository;
        this.clipboardEntryParser = clipboardEntryParser;
        this.youtubeUrlNormalizer = youtubeUrlNormalizer;
    }

    List<ContestEntry> findAll(long showId) {
        requireShow(showId);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    List<ContestEntry> reorder(long showId, ReorderContestEntriesRequest request) {
        requireShow(showId);
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
        requireShow(showId);
        return repository.create(
                showId,
                requiredText(request.artist(), "Der Interpret darf nicht leer sein."),
                requiredText(request.title(), "Der Titel darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(request.youtubeUrl()),
                optionalText(request.comment())
        );
    }

    @Transactional
    ContestEntry update(long showId, long entryId, UpdateContestEntryRequest request) {
        requireShow(showId);
        if (!repository.update(
                entryId,
                showId,
                requiredText(request.artist(), "Der Interpret darf nicht leer sein."),
                requiredText(request.title(), "Der Titel darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(request.youtubeUrl()),
                optionalText(request.comment())
        )) {
            throw new ContestEntryNotFoundException(entryId, showId);
        }
        return repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
    }

    @Transactional
    ContestEntry updateAssessment(long showId, long entryId, UpdateContestEntryAssessmentRequest request) {
        requireShow(showId);
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
        requireShow(showId);
        if (!repository.isBallotClosed(showId)) {
            throw new ApiConflictException(
                    "PARTICIPANT_ASSIGNMENT_REQUIRES_CLOSED_BALLOT",
                    "Teilnehmer können erst nach dem Abschluss der eigenen Top 15 zugeordnet werden."
            );
        }
        ContestEntry entry = repository.findByIdAndShowId(entryId, showId)
                .orElseThrow(() -> new ContestEntryNotFoundException(entryId, showId));
        Long participantId = request == null ? null : request.participantId();
        if (participantId != null) {
            if (!repository.participantExists(participantId)) {
                throw new ParticipantNotFoundException(participantId);
            }
            if (!repository.participantIsActive(participantId) && !participantId.equals(entry.participantId())) {
                throw new ApiConflictException(
                        "INACTIVE_PARTICIPANT_CANNOT_BE_ASSIGNED",
                        "Inaktive Teilnehmer können nicht neu einem Wettbewerbsbeitrag zugeordnet werden."
                );
            }
            repository.findEntryIdByParticipant(showId, participantId)
                    .filter(assignedEntryId -> assignedEntryId != entry.id())
                    .ifPresent(assignedEntryId -> {
                        throw duplicateParticipantAssignment();
                    });
        }
        try {
            if (!repository.updateParticipantAssignment(entryId, showId, participantId)) {
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
        requireShow(showId);
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
        requireShow(showId);
        String html = request == null ? null : request.html();
        String text = request == null ? null : request.text();
        if ((html == null || html.isBlank()) && (text == null || text.isBlank())) {
            throw new ApiBadRequestException("EMPTY_IMPORT_PREVIEW", "Es wurde kein Zwischenablageinhalt erkannt.");
        }
        return markPossibleDuplicates(clipboardEntryParser.parse(html, text), repository.findAllByShowId(showId));
    }

    @Transactional
    List<ContestEntry> importEntries(long showId, ImportContestEntriesRequest request) {
        requireShow(showId);
        if (request == null || request.entries() == null || request.entries().isEmpty()) {
            throw new ApiBadRequestException("EMPTY_IMPORT", "W\u00e4hle mindestens einen vollst\u00e4ndigen Beitrag f\u00fcr den Import aus.");
        }

        List<ValidatedImportEntry> validatedEntries = request.entries().stream().map(this::validateImportEntry).toList();
        for (ValidatedImportEntry entry : validatedEntries) {
            repository.create(showId, entry.artist(), entry.title(), entry.youtubeUrl(), entry.comment());
        }
        return repository.findAllByShowId(showId);
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

    private void requireShow(long showId) {
        if (!repository.showExists(showId)) {
            throw new ShowNotFoundException(showId);
        }
    }

    private static ApiConflictException duplicateParticipantAssignment() {
        return new ApiConflictException(
                "PARTICIPANT_ALREADY_ASSIGNED_IN_SHOW",
                "Ein Teilnehmer kann innerhalb derselben Mottoshow nur einem Wettbewerbsbeitrag zugeordnet werden."
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
            if (message != null && message.contains("contest_entry.motto_show_id, contest_entry.participant_id")) {
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
}
