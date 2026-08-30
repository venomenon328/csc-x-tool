package de.venomenon.cscxtool.show;

import de.venomenon.cscxtool.contest.ContestNotFoundException;
import de.venomenon.cscxtool.contest.ContestRepository;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class MottoShowService {

    private final MottoShowRepository repository;
    private final ContestRepository contests;

    MottoShowService(MottoShowRepository repository, ContestRepository contests) {
        this.repository = repository;
        this.contests = contests;
    }

    List<MottoShow> findAll(Long contestId) {
        long resolvedContestId = contestId == null
                ? contests.findCurrent().orElseThrow(() -> new IllegalStateException("No current contest exists.")).id()
                : contestId;
        if (!contests.exists(resolvedContestId)) {
            throw new ContestNotFoundException(resolvedContestId);
        }
        return repository.findAll(resolvedContestId);
    }

    MottoShow findById(long showId) {
        return repository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    MottoShow rename(long showId, String name) {
        String normalizedName = name.trim();
        if (!repository.rename(showId, normalizedName)) {
            throw new ShowNotFoundException(showId);
        }
        return repository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    @Transactional
    MottoShow createHistorical(long contestId, CreateMottoShowRequest request) {
        contests.findById(contestId).orElseThrow(() -> new ContestNotFoundException(contestId));
        if (contests.findById(contestId).orElseThrow().current()) {
            throw new ApiConflictException(
                    "CURRENT_CONTEST_SHOW_MANAGEMENT_FORBIDDEN",
                    "Mottoshows werden für die aktuelle CSC-Ausgabe weiterhin durch den bestehenden Workflow verwaltet."
            );
        }
        String name = request.name().trim();
        if (repository.showNumberExists(contestId, request.showNumber())) throw duplicateShowNumber();
        try {
            return repository.create(contestId, request.showNumber(), name);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateShowNumber();
        }
    }

    @Transactional
    void deleteHistorical(long contestId, long showId) {
        ShowContext context = context(showId);
        if (context.contestId() != contestId) throw new ShowNotFoundException(showId);
        requireHistorical(context);
        if (repository.hasReferences(showId)) {
            throw new ApiConflictException(
                    "HISTORICAL_SHOW_IN_USE",
                    "Eine Mottoshow mit Beiträgen oder weiteren Referenzen kann nicht gelöscht werden."
            );
        }
        if (!repository.delete(contestId, showId)) throw new ShowNotFoundException(showId);
    }

    @Transactional
    MottoShow updateHistorical(long contestId, long showId, UpdateHistoricalMottoShowRequest request) {
        ShowContext context = context(showId);
        if (context.contestId() != contestId) throw new ShowNotFoundException(showId);
        requireHistorical(context);
        if (repository.otherShowNumberExists(contestId, showId, request.showNumber())) throw duplicateShowNumber();
        try {
            if (!repository.updateHistorical(contestId, showId, request.showNumber(), request.name().trim())) {
                throw new ShowNotFoundException(showId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw duplicateShowNumber();
        }
        return repository.findById(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    ShowContext context(long showId) {
        return repository.findContext(showId).orElseThrow(() -> new ShowNotFoundException(showId));
    }

    void requireHistorical(ShowContext context) {
        if (context.currentContest()) {
            throw new ApiConflictException(
                    "HISTORICAL_SHOW_REQUIRED",
                    "Diese Funktion ist ausschließlich für historische CSC-Ausgaben verfügbar."
            );
        }
    }

    private static ApiConflictException duplicateShowNumber() {
        return new ApiConflictException(
                "DUPLICATE_SHOW_NUMBER",
                "Diese Shownummer ist in der CSC-Ausgabe bereits vergeben."
        );
    }
}
