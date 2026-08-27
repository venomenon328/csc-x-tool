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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import de.venomenon.cscxtool.show.ShowNotFoundException;
import de.venomenon.cscxtool.candidate.CandidateNotFoundException;
import de.venomenon.cscxtool.participant.ParticipantNotFoundException;

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

    @ExceptionHandler(CandidateNotFoundException.class)
    ResponseEntity<ApiError> candidateNotFound(CandidateNotFoundException exception, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "CANDIDATE_NOT_FOUND",
                "Der angeforderte Kandidat wurde in dieser Mottoshow nicht gefunden.",
                request
        );
    }

    @ExceptionHandler(ParticipantNotFoundException.class)
    ResponseEntity<ApiError> participantNotFound(ParticipantNotFoundException exception, HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                "PARTICIPANT_NOT_FOUND",
                "Der angeforderte Teilnehmer wurde nicht gefunden.",
                request
        );
    }

    @ExceptionHandler(ApiBadRequestException.class)
    ResponseEntity<ApiError> badRequest(ApiBadRequestException exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(ApiConflictException.class)
    ResponseEntity<ApiError> conflict(ApiConflictException exception, HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validationFailed(MethodArgumentNotValidException exception, HttpServletRequest request) {
        FieldError fieldError = exception.getBindingResult().getFieldError();
        String message = fieldError == null
                ? "Die übermittelten Daten sind ungültig."
                : fieldError.getDefaultMessage();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadableRequest(HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Die übermittelten Daten sind ungültig.",
                request
        );
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
