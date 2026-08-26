package com.premisave.listing.client;

import com.premisave.listing.exception.WalletServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * FallbackFactory (rather than a plain fallback class) so the actual
 * triggering cause (connection refused, timeout, 5xx, open circuit) is
 * logged instead of assumed — same reasoning as
 * AuthServiceClientFallbackFactory, applied here because a payment failure
 * is exactly the kind of thing worth diagnosing precisely.
 *
 * Throws WalletServiceUnavailableException rather than returning a graceful
 * "failed" WalletPaymentResponse: a graceful response with success=false
 * was indistinguishable from wallet-service genuinely declining the debit
 * (e.g. insufficient funds), so AdPromotionService ended up telling users
 * "your wallet may have insufficient funds, please top up" even when the
 * real problem was wallet-service being unreachable — misleading, since
 * topping up wouldn't fix that. PaymentService catches this specific
 * exception, still records the attempt as FAILED for the audit trail, then
 * re-throws so the caller gets the correct "service unavailable" signal.
 */
@Slf4j
@Component
public class WalletServiceClientFallbackFactory implements FallbackFactory<WalletServiceClient> {

    @Override
    public WalletServiceClient create(Throwable cause) {
        return request -> {
            log.warn("wallet-service call failed, using fallback: debitForService(reference={}) — {}: {}",
                    request.getReference(), cause.getClass().getSimpleName(), cause.getMessage());

            throw new WalletServiceUnavailableException(
                    "Payments are temporarily unavailable. Please try again shortly.");
        };
    }
}