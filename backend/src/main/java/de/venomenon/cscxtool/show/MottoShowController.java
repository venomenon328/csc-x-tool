package de.venomenon.cscxtool.show;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shows")
class MottoShowController {

    private final MottoShowService service;

    MottoShowController(MottoShowService service) {
        this.service = service;
    }

    @GetMapping
    List<MottoShowResponse> findAll(@RequestParam(required = false) Long contestId) {
        return service.findAll(contestId).stream().map(MottoShowResponse::from).toList();
    }

    @GetMapping("/{showId}")
    MottoShowResponse findById(@PathVariable long showId) {
        return MottoShowResponse.from(service.findById(showId));
    }

    @PatchMapping("/{showId}")
    MottoShowResponse rename(
            @PathVariable long showId,
            @Valid @RequestBody RenameMottoShowRequest request
    ) {
        return MottoShowResponse.from(service.rename(showId, request.name()));
    }
}
