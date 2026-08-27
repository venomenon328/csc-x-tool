package de.venomenon.cscxtool.ballot;

import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/ballot")
class BallotController {

    private final BallotService service;

    BallotController(BallotService service) {
        this.service = service;
    }

    @GetMapping
    BallotResponse find(@PathVariable long showId) {
        return service.find(showId);
    }

    @PutMapping("/reorder")
    BallotRankingResponse reorder(@PathVariable long showId, @Valid @RequestBody ReorderBallotRequest request) {
        return service.reorder(showId, request);
    }

    @PostMapping("/close")
    BallotResponse close(@PathVariable long showId) {
        return service.close(showId);
    }

    @PostMapping("/reopen")
    BallotResponse reopen(@PathVariable long showId) {
        return service.reopen(showId);
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> export(@PathVariable long showId) {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("top-15.txt", StandardCharsets.UTF_8).build().toString())
                .body(service.export(showId));
    }
}
