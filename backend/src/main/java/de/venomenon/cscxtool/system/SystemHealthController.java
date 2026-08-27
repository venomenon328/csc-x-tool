package de.venomenon.cscxtool.system;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
class SystemHealthController {

    @GetMapping("/health")
    SystemHealthResponse health() {
        return new SystemHealthResponse("UP");
    }

    record SystemHealthResponse(String status) {
    }
}
