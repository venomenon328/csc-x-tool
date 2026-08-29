package de.venomenon.cscxtool.entry;

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
@RequestMapping("/api/shows/{showId}/entries")
class ContestEntryController {

    private final ContestEntryService service;

    ContestEntryController(ContestEntryService service) {
        this.service = service;
    }

    @GetMapping
    List<ContestEntryResponse> findAll(@PathVariable long showId) {
        return service.findAll(showId).stream().map(ContestEntryResponse::from).toList();
    }

    @PutMapping("/reorder")
    List<ContestEntryResponse> reorder(
            @PathVariable long showId,
            @Valid @RequestBody ReorderContestEntriesRequest request
    ) {
        return service.reorder(showId, request).stream().map(ContestEntryResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContestEntryResponse create(@PathVariable long showId, @Valid @RequestBody CreateContestEntryRequest request) {
        return ContestEntryResponse.from(service.create(showId, request));
    }

    @PatchMapping("/{entryId}")
    ContestEntryResponse update(
            @PathVariable long showId,
            @PathVariable long entryId,
            @Valid @RequestBody UpdateContestEntryRequest request
    ) {
        return ContestEntryResponse.from(service.update(showId, entryId, request));
    }

    @PutMapping("/{entryId}/participant")
    ContestEntryResponse updateParticipantAssignment(
            @PathVariable long showId,
            @PathVariable long entryId,
            @RequestBody(required = false) UpdateParticipantAssignmentRequest request
    ) {
        return ContestEntryResponse.from(service.updateParticipantAssignment(showId, entryId, request));
    }

    @DeleteMapping("/{entryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable long showId, @PathVariable long entryId) {
        service.delete(showId, entryId);
    }

    @PostMapping("/import-preview")
    List<ImportPreviewLine> preview(@PathVariable long showId, @RequestBody(required = false) ImportPreviewRequest request) {
        return service.preview(showId, request);
    }

    @PostMapping("/import")
    List<ContestEntryResponse> importEntries(
            @PathVariable long showId,
            @RequestBody(required = false) ImportContestEntriesRequest request
    ) {
        return service.importEntries(showId, request).stream().map(ContestEntryResponse::from).toList();
    }
}
