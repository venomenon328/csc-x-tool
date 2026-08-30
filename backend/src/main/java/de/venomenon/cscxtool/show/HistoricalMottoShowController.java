package de.venomenon.cscxtool.show;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests/{contestId}/shows")
class HistoricalMottoShowController {

    private final MottoShowService service;

    HistoricalMottoShowController(MottoShowService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    MottoShowResponse create(
            @PathVariable long contestId, @Valid @RequestBody CreateMottoShowRequest request
    ) {
        return MottoShowResponse.from(service.createHistorical(contestId, request));
    }

    @DeleteMapping("/{showId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long contestId, @PathVariable long showId) {
        service.deleteHistorical(contestId, showId);
    }

    @PatchMapping("/{showId}")
    MottoShowResponse update(
            @PathVariable long contestId, @PathVariable long showId, @Valid @RequestBody UpdateHistoricalMottoShowRequest request
    ) {
        return MottoShowResponse.from(service.updateHistorical(contestId, showId, request));
    }
}
