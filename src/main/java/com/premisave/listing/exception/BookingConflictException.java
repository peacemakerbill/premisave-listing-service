package com.premisave.listing.exception;

/**
 * Thrown when a requested booking date range overlaps with an existing
 * CONFIRMED or PENDING booking on the same listing. Mapped to HTTP 409
 * (Conflict) by GlobalExceptionHandler — distinct from a validation error
 * (400) or a not-found (404), since the request itself is well-formed and
 * the listing exists; it's specifically unavailable for those dates.
 */
@SuppressWarnings("serial")
public class BookingConflictException extends RuntimeException {
    public BookingConflictException(String message) {
        super(message);
    }
}