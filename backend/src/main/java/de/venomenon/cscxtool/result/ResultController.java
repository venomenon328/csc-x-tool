package de.venomenon.cscxtool.result;

import de.venomenon.cscxtool.shared.ApiConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/results")
class ResultController {
    private final ResultService service;
    ResultController(ResultService service) { this.service = service; }

    @GetMapping
    ResultResponse find(@PathVariable long showId) { return service.find(showId); }

    /** A deliberately separate compatibility endpoint; normal result UI never calls it. */
    @GetMapping("/legacy")
    LegacyResultResponse legacy(@PathVariable long showId) { return service.legacy(showId); }

    /** Explicit cleanup action after the user has completed the new ballot-based migration. */
    @DeleteMapping("/legacy")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteLegacy(@PathVariable long showId, @RequestParam(defaultValue = "false") boolean confirm) {
        if (!confirm) {
            throw new ApiConflictException(
                    "LEGACY_DELETE_CONFIRMATION_REQUIRED",
                    "Legacy-Ergebnisdaten werden nur nach ausdrücklicher Bestätigung entfernt."
            );
        }
        service.deleteLegacy(showId);
    }
}
