package de.venomenon.cscxtool.participant;

import de.venomenon.cscxtool.contest.CreateContestParticipationRequest;
import de.venomenon.cscxtool.contest.UpdateContestParticipationRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests/{contestId}/participants")
class ContestParticipantController {

    private final ParticipantService service;

    ContestParticipantController(ParticipantService service) {
        this.service = service;
    }

    @GetMapping
    List<ContestParticipantResponse> findAll(
            @PathVariable long contestId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String includeInactive
    ) {
        return service.findAllForContest(contestId, q, parseIncludeInactive(includeInactive));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContestParticipantResponse create(
            @PathVariable long contestId, @Valid @RequestBody CreateContestParticipationRequest request
    ) {
        return service.createParticipation(contestId, request);
    }

    @PatchMapping("/{participantId}")
    ContestParticipantResponse update(
            @PathVariable long contestId, @PathVariable long participantId,
            @Valid @RequestBody UpdateContestParticipationRequest request
    ) {
        return service.updateParticipation(contestId, participantId, request);
    }

    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long contestId, @PathVariable long participantId) {
        service.deleteParticipation(contestId, participantId);
    }

    private static boolean parseIncludeInactive(String value) {
        if (value == null || value.isBlank() || "false".equalsIgnoreCase(value)) return false;
        if ("true".equalsIgnoreCase(value)) return true;
        throw new de.venomenon.cscxtool.shared.ApiBadRequestException(
                "INVALID_INCLUDE_INACTIVE", "includeInactive muss true oder false sein."
        );
    }
}
