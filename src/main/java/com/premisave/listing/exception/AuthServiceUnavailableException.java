package com.premisave.listing.exception;

/**
 * Thrown when auth-service itself is unreachable (down, timing out,
 * connection refused) — as opposed to AuthenticationFailedException, which
 * means auth-service responded and simply found no valid user for the
 * given token.
 *
 * The distinction matters for the message shown to the caller: telling
 * someone to "log in again" when the real problem is auth-service being
 * down is actively misleading, since re-authenticating wouldn't fix
 * anything either (login itself likely goes through the same downed
 * service). Mapped to HTTP 503 by GlobalExceptionHandler.
 */
@SuppressWarnings("serial")
public class AuthServiceUnavailableException extends RuntimeException {
    public AuthServiceUnavailableException(String message) {
        super(message);
    }
}