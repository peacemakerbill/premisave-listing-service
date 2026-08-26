package com.premisave.listing.exception;

import com.premisave.listing.dto.ApiResponse;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Central exception handling. The guiding rule applied throughout: an
 * exception that represents an EXPECTED, recoverable condition (bad input,
 * a missing resource, an unreachable dependency, a failed login) is logged
 * at WARN with just its message — no stack trace, since there's nothing to
 * debug in the code itself. An exception that represents something
 * genuinely UNEXPECTED gets logged at ERROR with the full trace, because
 * that's the case actually worth digging into. Only the last two handlers
 * (RuntimeException, Exception) fall into the second category — everything
 * above them is a known, named condition.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ====================== BAD / MALFORMED REQUESTS (400) ======================

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
     * A required @RequestParam was missing entirely (e.g. calling
     * POST /listings/{id}/extend without ?days=). Previously fell through
     * to the generic Exception catch-all and returned a 500 — this is
     * clearly bad client input, not a server fault.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingParams(MissingServletRequestParameterException ex) {
        log.warn("Missing request parameter: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(
            false, "Missing required parameter: " + ex.getParameterName(), null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * A @RequestParam couldn't be converted to the expected type (e.g.
     * ?days=abc where an int is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<String>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Type mismatch for parameter '{}': {}", ex.getName(), ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(
            false, "Invalid value for parameter '" + ex.getName() + "'.", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * The request body was missing, empty, or not valid JSON for the
     * target DTO.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        log.warn("Malformed request body: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(
            false, "Request body is missing or malformed.", null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Bad input caught programmatically (e.g. PaymentService's amount/userId
     * checks, ListingController's image content-type check) rather than by
     * Bean Validation. Distinct from the generic RuntimeException handler
     * below so bad-input cases are visibly separate from domain errors in
     * the logs, though both currently return 400.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.warn("Invalid request: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // ====================== NOT FOUND (404) ======================

    /**
     * Resource-not-found — mapped to 404, distinct from a generic domain
     * RuntimeException (400).
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNotFoundException(NotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // ====================== AUTH (401 / 403) ======================

    /**
     * Identity verification against auth-service failed or couldn't be
     * completed. This is the exact case that used to print a full stack
     * trace for something as ordinary as "auth-service is temporarily
     * unreachable" — logged at WARN with just the message instead.
     */
    @ExceptionHandler(AuthenticationFailedException.class)
    public ResponseEntity<ApiResponse<String>> handleAuthenticationFailedException(AuthenticationFailedException ex) {
        log.warn("Authentication check failed: {}", ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage(), null);
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
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

    // ====================== DEPENDENCY FAILURES (503) ======================

    /**
     * A Feign call to auth-service or wallet-service failed in a way that
     * escaped the circuit-breaker fallback (both clients have one
     * configured — this is a defensive backstop, not the expected path).
     * Mapped to 503 rather than a generic 400/500, since the problem is a
     * downstream dependency, not this service or the caller's request.
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResponse<String>> handleFeignException(FeignException ex) {
        log.warn("Upstream service call failed: status={}, message={}", ex.status(), ex.getMessage());
        ApiResponse<String> response = new ApiResponse<>(
            false,
            "A required service is temporarily unavailable. Please try again shortly.",
            null
        );
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * MongoDB (or any Spring Data-backed store) is unreachable or errored.
     * Unlike the WARN-only handlers above, this gets a full stack trace —
     * a database failure is always worth investigating.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<String>> handleDataAccessException(DataAccessException ex) {
        log.error("Database error: {}", ex.getMessage(), ex);
        ApiResponse<String> response = new ApiResponse<>(
            false,
            "A database error occurred. Please try again shortly.",
            null
        );
        return new ResponseEntity<>(response, HttpStatus.SERVICE_UNAVAILABLE);
    }

    // ====================== EVERYTHING ELSE ======================

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<String>> handleRuntimeException(RuntimeException ex) {
        log.error("RuntimeException: {}", ex.getMessage(), ex);
        ApiResponse<String> response = new ApiResponse<>(
            false, ex.getMessage(), null
        );
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * Catch-all for anything unexpected. Full detail goes to the logs with
     * a correlation ID; the client only gets a generic message plus that ID
     * so a report can be matched back to the log entry without exposing
     * internals.
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