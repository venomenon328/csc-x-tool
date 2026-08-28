package de.venomenon.cscxtool.system;

import de.venomenon.cscxtool.shared.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class LocalSecurityConfiguration {

    @Bean
    SecurityFilterChain localSecurityFilterChain(
            HttpSecurity http, LocalSecurityProperties properties, Environment environment,
            ObjectMapper objectMapper, ShutdownLifecycleService shutdowns
    ) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, exception) ->
                        writeApiError(objectMapper, request, response, HttpServletResponse.SC_FORBIDDEN,
                                "CSRF_REJECTED", "Die Anfrage wurde aus Sicherheitsgründen abgewiesen.")))
                .addFilterBefore(new LocalRequestSecurityFilter(environment, objectMapper, shutdowns), CsrfFilter.class);

        if (properties.isCsrfEnabled()) {
            http.csrf(csrf -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()));
        } else {
            http.csrf(AbstractHttpConfigurer::disable);
        }
        return http.build();
    }

    private static void writeApiError(
            ObjectMapper objectMapper, HttpServletRequest request, HttpServletResponse response,
            int status, String code, String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError(Instant.now(), status, code, message, request.getRequestURI()));
    }

    /** Applies loopback Host validation to every API request and Origin validation before CSRF to mutations. */
    private static final class LocalRequestSecurityFilter extends OncePerRequestFilter {

        private final boolean developmentProfile;
        private final ObjectMapper objectMapper;
        private final ShutdownLifecycleService shutdowns;

        private LocalRequestSecurityFilter(Environment environment, ObjectMapper objectMapper, ShutdownLifecycleService shutdowns) {
            this.developmentProfile = java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev");
            this.objectMapper = objectMapper;
            this.shutdowns = shutdowns;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
        ) throws ServletException, IOException {
            if (!isApiRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!hasExpectedHost(request)) {
                writeApiError(objectMapper, request, response, HttpServletResponse.SC_FORBIDDEN,
                        "LOCAL_HOST_REQUIRED", "API-Zugriffe sind nur über die lokale Anwendung erlaubt.");
                return;
            }
            if (!isMutatingApiRequest(request)) {
                filterChain.doFilter(request, response);
                return;
            }
            if (!hasExpectedOrigin(request)) {
                writeApiError(objectMapper, request, response, HttpServletResponse.SC_FORBIDDEN,
                        "LOCAL_ORIGIN_REQUIRED", "Schreibende Befehle sind nur aus der lokalen Anwendung erlaubt.");
                return;
            }
            if (shutdowns.isShutdownRequested() && !"/api/system/shutdown".equals(request.getRequestURI())) {
                writeApiError(objectMapper, request, response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        "SHUTDOWN_IN_PROGRESS", "Die Anwendung wird bereits beendet.");
                return;
            }
            filterChain.doFilter(request, response);
        }

        private static boolean isApiRequest(HttpServletRequest request) {
            return request.getRequestURI().startsWith("/api/");
        }

        private static boolean isMutatingApiRequest(HttpServletRequest request) {
            String method = request.getMethod().toUpperCase(Locale.ROOT);
            return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
        }

        private static boolean hasExpectedHost(HttpServletRequest request) {
            String expected = "127.0.0.1:" + request.getLocalPort();
            return expected.equalsIgnoreCase(request.getHeader("Host"));
        }

        private boolean hasExpectedOrigin(HttpServletRequest request) {
            String origin = request.getHeader("Origin");
            if (origin == null || origin.isBlank()) return true;
            String ownOrigin = "http://127.0.0.1:" + request.getLocalPort();
            return ownOrigin.equals(origin) || (developmentProfile && "http://127.0.0.1:5173".equals(origin));
        }
    }
}
