package com.premisave.listing.exception;

/**
 * Thrown when identity verification against auth-service fails or cannot
 * be completed — e.g. auth-service returns no user for the given token, or
 * (via the Feign fallback) auth-service is unreachable entirely. Mapped to
 * HTTP 401 by GlobalExceptionHandler and logged at WARN without a full
 * stack trace: this is an expected, recoverable condition (retry, check
 * whether auth-service is actually up, re-authenticate), not a bug that
 * needs an ERROR-level trace every time it happens.
 */
@SuppressWarnings("serial")
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}