package de.venomenon.cscxtool.shared;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import de.venomenon.cscxtool.show.ShowNotFoundException;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(ShowNotFoundException.class)
    ResponseEntity<ApiError> showNotFound(ShowNotFoundException exception, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "SHOW_NOT_FOUND",
                "Die angeforderte Mottoshow wurde nicht gefunden.",
                request
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "Die übermittelten Daten sind ungültig."
                : fieldError.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> noResourceFound(HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND",
                "Die angeforderte Ressource wurde nicht gefunden.",
                request
        );
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, HttpServletRequest request) {
        ApiError error = new ApiError(Instant.now(), status.value(), code, message, request.getRequestURI());
        return ResponseEntity.status(status).header(HttpHeaders.CONTENT_TYPE, "application/json").body(error);
    }
}
