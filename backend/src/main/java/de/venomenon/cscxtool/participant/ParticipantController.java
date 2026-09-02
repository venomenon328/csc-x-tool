package de.venomenon.cscxtool.participant;

import de.venomenon.cscxtool.shared.ApiBadRequestException;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/participants")
class ParticipantController {

    private final ParticipantService service;

    ParticipantController(ParticipantService service) {
        this.service = service;
    }

    @GetMapping
    List<ParticipantResponse> findAll(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String includeInactive
    ) {
        return service.findAll(q, parseIncludeInactive(includeInactive));
    }

    @GetMapping("/{participantId}")
    ParticipantResponse findById(@PathVariable long participantId) {
        return service.findById(participantId);
    }

    @GetMapping("/{participantId}/botb-selections")
    List<BotbSelectionResponse> findBotbSelections(@PathVariable long participantId) {
        return service.findBotbSelections(participantId);
    }

    @PutMapping("/{participantId}/botb-selections")
    List<BotbSelectionResponse> replaceBotbSelections(
            @PathVariable long participantId,
            @Valid @RequestBody List<BotbSelectionRequest> request
    ) {
        return service.replaceBotbSelections(participantId, request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ParticipantResponse create(@Valid @RequestBody CreateParticipantRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{participantId}")
    ParticipantResponse update(
            @PathVariable long participantId,
            @Valid @RequestBody UpdateParticipantRequest request
    ) {
        return service.update(participantId, request);
    }

    @DeleteMapping("/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long participantId) {
        service.delete(participantId);
    }

    private static boolean parseIncludeInactive(String value) {
        if (value == null || value.isBlank() || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new ApiBadRequestException("INVALID_INCLUDE_INACTIVE", "includeInactive muss true oder false sein.");
    }
}
