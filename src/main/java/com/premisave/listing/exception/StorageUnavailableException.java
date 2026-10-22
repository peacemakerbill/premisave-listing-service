package com.premisave.listing.exception;

/**
 * The image folder cannot be used right now: the network share is not
 * mounted, the disk is full or not writable. Mapped to HTTP 503 by
 * GlobalExceptionHandler, the same way an unreachable auth-service or
 * wallet-service is.
 */
@SuppressWarnings("serial")
public class StorageUnavailableException extends RuntimeException {
    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}