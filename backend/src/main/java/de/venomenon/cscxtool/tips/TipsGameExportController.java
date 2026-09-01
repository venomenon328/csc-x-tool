package de.venomenon.cscxtool.tips;

import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows/{showId}/tips")
class TipsGameExportController {

    private final TipsGameExportService service;

    TipsGameExportController(TipsGameExportService service) {
        this.service = service;
    }

    @GetMapping(value = "/export", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> export(@PathVariable long showId) {
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("tippspiel-zuordnungen.txt", StandardCharsets.UTF_8)
                        .build().toString())
                .body(service.export(showId));
    }
}
