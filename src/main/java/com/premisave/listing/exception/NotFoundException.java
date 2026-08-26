package com.premisave.listing.exception;

/**
 * Thrown when a requested resource does not exist. Mapped to HTTP 404 by
 * GlobalExceptionHandler — unlike a plain RuntimeException, which maps to
 * 400 and previously covered every "not found" case too, misleading any
 * client relying on standard HTTP status semantics.
 */
@SuppressWarnings("serial")
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}