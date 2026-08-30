package de.venomenon.cscxtool.show;

import de.venomenon.cscxtool.contest.ContestNotFoundException;
import de.venomenon.cscxtool.contest.ContestRepository;
import java.util.List;
import org.springframework.stereotype.Service;

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
}
