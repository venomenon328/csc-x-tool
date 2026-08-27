package de.venomenon.cscxtool.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResourceFound(HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ApiError error = new ApiError(
                Instant.now(),
                status.value(),
                "RESOURCE_NOT_FOUND",
                "Die angeforderte Ressource wurde nicht gefunden.",
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(error);
    }
}
