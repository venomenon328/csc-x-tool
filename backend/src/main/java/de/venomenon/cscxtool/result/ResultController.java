package de.venomenon.cscxtool.result;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/results")
class ResultController {

    private final ResultService service;

    ResultController(ResultService service) {
        this.service = service;
    }

    @GetMapping
    ResultResponse find(@PathVariable long showId) {
        return service.find(showId);
    }

    @PutMapping("/scores/{participantId}")
    ResultResponse updateScore(
            @PathVariable long showId,
            @PathVariable long participantId,
            @Valid @RequestBody UpdateReceivedScoreRequest request
    ) {
        return service.updateScore(showId, participantId, request);
    }

    @PutMapping("/details")
    ResultResponse updateDetails(@PathVariable long showId, @RequestBody UpdateResultDetailsRequest request) {
        return service.updateDetails(showId, request);
    }

    @PostMapping("/close")
    ResultResponse close(@PathVariable long showId) {
        return service.close(showId);
    }

    @PostMapping("/reopen")
    ResultResponse reopen(@PathVariable long showId) {
        return service.reopen(showId);
    }
}
