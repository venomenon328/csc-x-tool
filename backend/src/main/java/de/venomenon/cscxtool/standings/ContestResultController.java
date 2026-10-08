package de.venomenon.cscxtool.standings;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class ContestResultController {
    private final ContestResultService service;
    ContestResultController(ContestResultService service) { this.service = service; }
    @GetMapping("/api/contests/{contestId}/standings")
    ContestStandingsResponse standings(@PathVariable long contestId) { return service.standings(contestId); }
    @GetMapping("/api/contests/{contestId}/statistics")
    ContestStatisticsResponse statistics(@PathVariable long contestId) { return service.statistics(contestId); }
    @GetMapping("/api/contests/{contestId}/shows/{showId}/ballot-impact/{ballotId}")
    BallotImpactResponse impact(@PathVariable long contestId, @PathVariable long showId, @PathVariable long ballotId) {
        return service.impact(contestId, showId, ballotId);
    }
    @GetMapping("/api/shows/{showId}/result-closure")
    ShowResultClosureResponse closure(@PathVariable long showId) { return service.closure(showId); }
    @PostMapping("/api/shows/{showId}/result-closure/close")
    ShowResultClosureResponse close(@PathVariable long showId) { return service.close(showId); }
    @PostMapping("/api/shows/{showId}/result-closure/reopen")
    ShowResultClosureResponse reopen(@PathVariable long showId) { return service.reopen(showId); }
}
