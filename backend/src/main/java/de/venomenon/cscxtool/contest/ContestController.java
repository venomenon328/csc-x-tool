package de.venomenon.cscxtool.contest;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contests")
class ContestController {

    private final ContestService service;

    ContestController(ContestService service) {
        this.service = service;
    }

    @GetMapping
    List<ContestResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/current")
    ContestResponse current() {
        return service.current();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ContestResponse create(@Valid @RequestBody CreateContestRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{contestId}")
    ContestResponse rename(@PathVariable long contestId, @Valid @RequestBody RenameContestRequest request) {
        return service.rename(contestId, request);
    }

    @PostMapping("/{contestId}/make-current")
    ContestResponse makeCurrent(@PathVariable long contestId) {
        return service.makeCurrent(contestId);
    }
}
