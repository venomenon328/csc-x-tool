package de.venomenon.cscxtool.contest;

import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ContestService {

    private final ContestRepository repository;

    ContestService(ContestRepository repository) {
        this.repository = repository;
    }

    List<ContestResponse> findAll() {
        return repository.findAll().stream().map(ContestResponse::from).toList();
    }

    ContestResponse current() {
        return ContestResponse.from(repository.findCurrent().orElseThrow(() -> new ApiConflictException(
                "CURRENT_CONTEST_MISSING", "Es ist keine aktuelle CSC-Ausgabe hinterlegt."
        )));
    }

    @Transactional
    ContestResponse create(CreateContestRequest request) {
        String name = requiredName(request.name());
        if (repository.nameExists(name)) {
            throw duplicateName();
        }
        try {
            return ContestResponse.from(repository.create(name));
        } catch (DataAccessException exception) {
            throw duplicateNameIfUniqueNameConstraint(exception);
        }
    }

    @Transactional
    ContestResponse rename(long contestId, RenameContestRequest request) {
        String name = requiredName(request.name());
        if (repository.otherContestHasName(contestId, name)) {
            throw duplicateName();
        }
        try {
            if (!repository.rename(contestId, name)) {
                throw new ContestNotFoundException(contestId);
            }
            return ContestResponse.from(repository.findById(contestId).orElseThrow(() -> new ContestNotFoundException(contestId)));
        } catch (DataAccessException exception) {
            throw duplicateNameIfUniqueNameConstraint(exception);
        }
    }

    @Transactional
    ContestResponse makeCurrent(long contestId) {
        requireContest(contestId);
        repository.makeCurrent(contestId);
        return ContestResponse.from(repository.findById(contestId).orElseThrow(() -> new ContestNotFoundException(contestId)));
    }

    @Transactional
    ContestResponse setOwnParticipation(long contestId, SetOwnParticipationRequest request) {
        Contest contest = repository.findById(contestId).orElseThrow(() -> new ContestNotFoundException(contestId));
        Long nextParticipationId = request == null ? null : request.participationId();
        if (nextParticipationId != null && repository.findParticipations(contestId).stream().noneMatch(participation -> participation.id() == nextParticipationId)) {
            throw new ApiConflictException(
                    "OWN_PARTICIPATION_NOT_IN_CONTEST",
                    "Die eigene Teilnahme muss zu dieser CSC-Ausgabe gehören."
            );
        }
        if (!java.util.Objects.equals(contest.ownParticipationId(), nextParticipationId)
                && contest.ownParticipationId() != null
                && repository.hasDerivedOwnResults(contestId, contest.ownParticipationId())
                && (request == null || !request.isConfirmed())) {
            throw new ApiConflictException(
                    "OWN_PARTICIPATION_CHANGE_CONFIRMATION_REQUIRED",
                    "Die eigene Teilnahme wird bereits für abgeleitete Ergebnisse verwendet und muss bewusst geändert werden."
            );
        }
        if (!java.util.Objects.equals(contest.ownParticipationId(), nextParticipationId)
                && nextParticipationId != null
                && repository.currentSnapshotContainsParticipation(contestId, nextParticipationId)) {
            throw new ApiConflictException(
                    "OWN_PARTICIPATION_CHANGE_REQUIRES_BALLOT_REOPEN",
                    "Die neue eigene Teilnahme besitzt einen Beitrag in einem aktuellen Top-15-Snapshot. Öffne diese Abstimmung zuerst bewusst wieder."
            );
        }
        if (!java.util.Objects.equals(contest.ownParticipationId(), nextParticipationId)) {
            List<Long> openOwnEntryIds = repository.findOpenResolvedOwnEntryIds(contestId);
            repository.resetOwnEntryResolutions(contestId);
            repository.clearEntryAssignments(openOwnEntryIds);
        }
        repository.updateOwnParticipation(contestId, nextParticipationId);
        return ContestResponse.from(repository.findById(contestId).orElseThrow(() -> new ContestNotFoundException(contestId)));
    }

    void requireContest(long contestId) {
        if (!repository.exists(contestId)) {
            throw new ContestNotFoundException(contestId);
        }
    }

    private static String requiredName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new de.venomenon.cscxtool.shared.ApiBadRequestException("VALIDATION_ERROR", "Der Contestname darf nicht leer sein.");
        }
        return name.trim();
    }

    private static ApiConflictException duplicateName() {
        return new ApiConflictException("DUPLICATE_CONTEST_NAME", "Eine CSC-Ausgabe mit diesem Namen existiert bereits.");
    }

    private static RuntimeException duplicateNameIfUniqueNameConstraint(DataAccessException exception) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("UNIQUE constraint failed: contest.name")) {
                return duplicateName();
            }
        }
        return exception;
    }
}
