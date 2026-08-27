package de.venomenon.cscxtool.candidate;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}")
class CandidateController {

    private final CandidateService service;

    CandidateController(CandidateService service) {
        this.service = service;
    }

    @GetMapping("/candidates")
    List<CandidateResponse> findAll(@PathVariable long showId) {
        return service.findAll(showId).stream().map(CandidateResponse::from).toList();
    }

    @PostMapping("/candidates")
    @ResponseStatus(HttpStatus.CREATED)
    CandidateResponse create(@PathVariable long showId, @Valid @RequestBody CreateCandidateRequest request) {
        return CandidateResponse.from(service.create(showId, request));
    }

    @PatchMapping("/candidates/{candidateId}")
    CandidateResponse update(
            @PathVariable long showId,
            @PathVariable long candidateId,
            @Valid @RequestBody UpdateCandidateRequest request
    ) {
        return CandidateResponse.from(service.update(showId, candidateId, request));
    }

    @DeleteMapping("/candidates/{candidateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long showId, @PathVariable long candidateId) {
        service.delete(showId, candidateId);
    }

    @PutMapping("/candidates/reorder")
    List<CandidateResponse> reorder(@PathVariable long showId, @Valid @RequestBody ReorderCandidatesRequest request) {
        return service.reorder(showId, request.candidateIds()).stream().map(CandidateResponse::from).toList();
    }

    @PostMapping("/candidates/{candidateId}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    List<CandidateResponse> copy(
            @PathVariable long showId,
            @PathVariable long candidateId,
            @Valid @RequestBody CopyCandidateRequest request
    ) {
        return service.copy(showId, candidateId, request.targetShowIds()).stream().map(CandidateResponse::from).toList();
    }

    @PutMapping("/submission")
    CandidateResponse selectSubmission(@PathVariable long showId, @Valid @RequestBody SelectSubmissionRequest request) {
        return CandidateResponse.from(service.selectSubmission(showId, request.candidateId(), request.confirmReplacement()));
    }

    @DeleteMapping("/submission")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void clearSubmission(@PathVariable long showId) {
        service.clearSubmission(showId);
    }
}
