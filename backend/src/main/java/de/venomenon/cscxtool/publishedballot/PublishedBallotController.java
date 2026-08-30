package de.venomenon.cscxtool.publishedballot;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/published-ballots")
class PublishedBallotController {
    private final PublishedBallotService service;
    PublishedBallotController(PublishedBallotService service) { this.service = service; }

    @GetMapping
    PublishedBallotOverviewResponse overview(@PathVariable long showId) { return service.overview(showId); }
    @GetMapping("/{participationId}")
    PublishedBallotDetailResponse detail(@PathVariable long showId, @PathVariable long participationId) { return service.detail(showId, participationId); }
    @PostMapping("/import-preview")
    List<PublishedBallotPreviewBlock> preview(@PathVariable long showId, @RequestBody(required = false) PublishedBallotImportPreviewRequest request) {
        return service.preview(showId, request);
    }
    @PostMapping("/import")
    PublishedBallotOverviewResponse importBallots(@PathVariable long showId, @RequestBody(required = false) PublishedBallotImportBatchRequest request) {
        return service.importBallots(showId, request);
    }
    @PutMapping("/{participationId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateStatus(@PathVariable long showId, @PathVariable long participationId, @RequestBody(required = false) UpdatePublishedBallotStatusRequest request) {
        service.updateStatus(showId, participationId, request);
    }
}
