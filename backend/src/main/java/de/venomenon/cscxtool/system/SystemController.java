package de.venomenon.cscxtool.system;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
class SystemController {

    private final ShutdownLifecycleService shutdowns;

    SystemController(ShutdownLifecycleService shutdowns) {
        this.shutdowns = shutdowns;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping("/shutdown")
    ResponseEntity<Map<String, String>> shutdown() {
        shutdowns.requestShutdown();
        return ResponseEntity.accepted().body(Map.of("status", "SHUTTING_DOWN"));
    }
}
