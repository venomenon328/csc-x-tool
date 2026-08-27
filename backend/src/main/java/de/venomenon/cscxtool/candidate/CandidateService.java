package de.venomenon.cscxtool.candidate;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import de.venomenon.cscxtool.shared.ApiConflictException;
import de.venomenon.cscxtool.song.YoutubeUrlNormalizer;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CandidateService {

    private final CandidateRepository repository;
    private final YoutubeUrlNormalizer youtubeUrlNormalizer;

    CandidateService(CandidateRepository repository, YoutubeUrlNormalizer youtubeUrlNormalizer) {
        this.repository = repository;
        this.youtubeUrlNormalizer = youtubeUrlNormalizer;
    }

    List<Candidate> findAll(long showId) {
        requireShow(showId);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    Candidate create(long showId, CreateCandidateRequest request) {
        requireShow(showId);
        return repository.append(
                showId,
                requiredText(request.artist(), "Der Interpret darf nicht leer sein."),
                requiredText(request.title(), "Der Titel darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(request.youtubeUrl()),
                optionalText(request.comment()),
                CandidateStatus.OFFEN
        );
    }

    @Transactional
    Candidate update(long showId, long candidateId, UpdateCandidateRequest request) {
        requireShow(showId);
        if (!repository.update(
                candidateId,
                showId,
                requiredText(request.artist(), "Der Interpret darf nicht leer sein."),
                requiredText(request.title(), "Der Titel darf nicht leer sein."),
                youtubeUrlNormalizer.normalize(request.youtubeUrl()),
                optionalText(request.comment()),
                request.status()
        )) {
            throw new CandidateNotFoundException(candidateId, showId);
        }
        return repository.findByIdAndShowId(candidateId, showId).orElseThrow(() -> new CandidateNotFoundException(candidateId, showId));
    }

    @Transactional
    void delete(long showId, long candidateId) {
        requireShow(showId);
        Candidate candidate = repository.findByIdAndShowId(candidateId, showId)
                .orElseThrow(() -> new CandidateNotFoundException(candidateId, showId));
        if (repository.isSelectedForShow(candidateId, showId)) {
            throw new ApiConflictException(
                    "SELECTED_CANDIDATE_CANNOT_BE_DELETED",
                    "Die aktive eigene Einreichung muss vor dem Löschen bewusst aufgehoben oder ersetzt werden."
            );
        }
        if (!repository.delete(candidateId, showId)) {
            throw new CandidateNotFoundException(candidateId, showId);
        }
        repository.closePositionGap(showId, candidate.manualPosition());
    }

    @Transactional
    List<Candidate> reorder(long showId, List<Long> orderedCandidateIds) {
        requireShow(showId);
        List<Candidate> currentCandidates = repository.findAllByShowId(showId);
        Set<Long> currentIds = currentCandidates.stream().map(Candidate::id).collect(java.util.stream.Collectors.toSet());
        Set<Long> submittedIds = new HashSet<>(orderedCandidateIds);
        if (currentIds.size() != orderedCandidateIds.size() || submittedIds.size() != orderedCandidateIds.size()
                || !currentIds.equals(submittedIds)) {
            throw new ApiConflictException(
                    "CANDIDATE_REORDER_CONFLICT",
                    "Die Reihenfolge enthält nicht exakt alle aktuellen Kandidaten dieser Mottoshow."
            );
        }
        repository.reorder(showId, orderedCandidateIds);
        return repository.findAllByShowId(showId);
    }

    @Transactional
    List<Candidate> copy(long sourceShowId, long candidateId, List<Long> targetShowIds) {
        requireShow(sourceShowId);
        Candidate source = repository.findByIdAndShowId(candidateId, sourceShowId)
                .orElseThrow(() -> new CandidateNotFoundException(candidateId, sourceShowId));

        Set<Long> uniqueTargetShowIds = new HashSet<>(targetShowIds);
        if (uniqueTargetShowIds.size() != targetShowIds.size()) {
            throw new ApiBadRequestException("DUPLICATE_COPY_TARGET", "Jede Zielshow darf nur einmal ausgewählt werden.");
        }
        if (uniqueTargetShowIds.contains(sourceShowId)) {
            throw new ApiBadRequestException("SOURCE_SHOW_COPY_TARGET", "Die Quellshow kann nicht zugleich Zielshow sein.");
        }
        if (repository.existingShowIds(uniqueTargetShowIds).size() != uniqueTargetShowIds.size()) {
            throw new ApiBadRequestException("COPY_TARGET_SHOW_NOT_FOUND", "Mindestens eine Zielshow wurde nicht gefunden.");
        }

        return targetShowIds.stream().map(targetShowId -> repository.append(
                targetShowId,
                source.artist(),
                source.title(),
                source.youtubeUrl(),
                source.comment(),
                CandidateStatus.OFFEN
        )).toList();
    }

    @Transactional
    Candidate selectSubmission(long showId, long candidateId, boolean confirmReplacement) {
        requireShow(showId);
        requireResultsOpenForSubmissionChange(showId);
        Candidate candidate = repository.findByIdAndShowId(candidateId, showId)
                .orElseThrow(() -> new CandidateNotFoundException(candidateId, showId));
        Long currentlySelectedCandidateId = repository.selectedCandidateId(showId);
        if (currentlySelectedCandidateId != null && currentlySelectedCandidateId != candidateId && !confirmReplacement) {
            throw new ApiConflictException(
                    "SUBMISSION_REPLACEMENT_CONFIRMATION_REQUIRED",
                    "Eine bestehende Einreichung wird erst nach ausdrücklicher Bestätigung ersetzt."
            );
        }
        if (currentlySelectedCandidateId == null || currentlySelectedCandidateId != candidateId) {
            repository.selectSubmission(showId, candidateId);
        }
        return candidate;
    }

    @Transactional
    void clearSubmission(long showId) {
        requireShow(showId);
        requireResultsOpenForSubmissionChange(showId);
        repository.clearSubmission(showId);
    }

    private void requireResultsOpenForSubmissionChange(long showId) {
        if (repository.resultsClosed(showId)) {
            throw new ApiConflictException(
                    "RESULTS_REOPEN_REQUIRED_FOR_SUBMISSION_CHANGE",
                    "Die abgeschlossene Ergebniserfassung muss vor dem Wechsel oder Aufheben der eigenen Einreichung bewusst wieder geöffnet werden."
            );
        }
    }

    private void requireShow(long showId) {
        if (!repository.showExists(showId)) {
            throw new ShowNotFoundException(showId);
        }
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
}
