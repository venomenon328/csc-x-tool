package de.venomenon.cscxtool.system;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Keeps the local application same-origin while allowing only the privacy-enhanced YouTube frame. */
@Component
class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    private static final String POLICY = "default-src 'self'; "
            + "base-uri 'none'; form-action 'self'; object-src 'none'; frame-ancestors 'none'; "
            + "frame-src https://www.youtube-nocookie.com; "
            + "img-src 'self' data:; font-src 'self' data:; style-src 'self' 'unsafe-inline'";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", POLICY);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "clipboard-read=(self), clipboard-write=(self)");
        filterChain.doFilter(request, response);
    }
}
