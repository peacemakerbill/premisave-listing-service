package com.premisave.listing.exception;

/**
 * Thrown when wallet-service itself is unreachable (down, timing out,
 * connection refused) — as opposed to wallet-service responding and
 * genuinely declining the debit (e.g. insufficient funds).
 *
 * The distinction matters for the message shown to the caller: telling
 * someone "your wallet may have insufficient funds, please top up" when
 * the real problem is wallet-service being down is actively misleading —
 * topping up won't fix an unreachable service. Mapped to HTTP 503 by
 * GlobalExceptionHandler.
 */
@SuppressWarnings("serial")
public class WalletServiceUnavailableException extends RuntimeException {
    public WalletServiceUnavailableException(String message) {
        super(message);
    }
}