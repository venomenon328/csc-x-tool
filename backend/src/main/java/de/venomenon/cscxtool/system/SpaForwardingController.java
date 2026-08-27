package de.venomenon.cscxtool.system;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class SpaForwardingController {

    /**
     * Only known browser routes are forwarded. API paths deliberately have no
     * catch-all here, so an unknown API endpoint remains a real HTTP 404.
     */
    @GetMapping({
            "/participants",
            "/data",
            "/shows/{showId}/candidates",
            "/shows/{showId}/voting",
            "/shows/{showId}/result"
    })
    String forwardToSpa() {
        return "forward:/index.html";
    }
}
