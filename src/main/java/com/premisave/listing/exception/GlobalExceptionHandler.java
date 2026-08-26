package com.premisave.listing.exception;

import com.premisave.listing.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
            errors.put(error.getField(), error.getDefaultMessage())
        );

        ApiResponse<Map<String, String>> response = new ApiResponse<>(
            false, "Validation failed", errors
        );

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Resource-not-found — mapped to 404. Previously every "not found" case
     * (payment, listing, promotion) was thrown as a generic RuntimeException
     * and fell through to the handler below, returning 400 — which misleads
     * any client relying on standard HTTP status semantics (e.g. retry
     * logic keyed on status code, or simply distinguishing "bad input" from
     * "doesn't exist").
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotFoundException(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle Access Denied (403) - When user doesn't have required role (ADMIN/FINANCE)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access Denied: {}", ex.getMessage());

        ApiResponse<String> response = new ApiResponse<>(
            false,
            "Access Denied: You do not have permission to access this admin resource. " +
            "This endpoint requires ADMIN or FINANCE role.",
            null
        );

        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage(), ex);
        ApiResponse<String> response = new ApiResponse<>(
            false, ex.getMessage(), null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catch-all for anything unexpected. Previously this returned the raw
     * exception class name and message directly in the response body —
     * useful in local dev, but an information-disclosure risk on a public
     * API. Full detail still goes to the logs against a correlation ID; the
     * client only receives a generic message plus that ID so a report can be
     * matched back to the log entry without exposing internals.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGeneralException(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unexpected error occurred [correlationId={}]: {}", correlationId, ex.getMessage(), ex);
        ApiResponse<String> response = new ApiResponse<>(
            false,
            "An unexpected error occurred. Reference: " + correlationId,
            null
        );
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}