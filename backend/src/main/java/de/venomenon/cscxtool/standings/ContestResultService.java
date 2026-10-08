package de.venomenon.cscxtool.standings;

import de.venomenon.cscxtool.participant.CountryCatalog;
import de.venomenon.cscxtool.shared.ApiConflictException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContestResultService {
    private final ContestResultRepository repository;
    private final CountryCatalog countries;
    public ContestResultService(ContestResultRepository repository, CountryCatalog countries) {
        this.repository = repository; this.countries = countries;
    }

    @Transactional(readOnly = true)
    public ContestStandingsResponse standings(long contestId) {
        return ContestStandings.calculate(repository.load(contestId), countries);
    }

    @Transactional(readOnly = true)
    public ContestStatisticsResponse statistics(long contestId) {
        return ContestStatistics.calculate(repository.load(contestId), countries);
    }

    @Transactional(readOnly = true)
    public ShowResultClosureResponse closure(long showId) {
        return response(repository.load(repository.contestForShow(showId)), showId);
    }

    @Transactional
    public ShowResultClosureResponse close(long showId) {
        var data = repository.load(repository.contestForShow(showId));
        var response = response(data, showId);
        if (response.closedAt() != null) return response;
        if (!response.reasons().isEmpty()) throw new ApiConflictException("SHOW_RESULT_NOT_READY", String.join(" ", response.reasons()));
        repository.setClosed(showId, Instant.now().toString());
        return response(repository.load(data.contestId()), showId);
    }

    @Transactional
    public ShowResultClosureResponse reopen(long showId) {
        long contestId = repository.contestForShow(showId);
        repository.setClosed(showId, null);
        return response(repository.load(contestId), showId);
    }

    static String status(ResultData data, ResultData.Show show) {
        return show.closedAt() != null ? "CLOSED" : ResultClosureRules.reasons(data, show).isEmpty() ? "READY" : "IN_PROGRESS";
    }
    private static ShowResultClosureResponse response(ResultData data, long showId) {
        var show = data.shows().stream().filter(s -> s.id() == showId).findFirst().orElseThrow();
        return new ShowResultClosureResponse(showId, data.contestId(), status(data, show), show.closedAt(), ResultClosureRules.reasons(data, show));
    }
}
