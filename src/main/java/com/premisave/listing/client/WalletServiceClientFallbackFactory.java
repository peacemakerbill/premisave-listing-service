package com.premisave.listing.client;

import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * FallbackFactory (rather than a plain fallback class) so the actual
 * triggering cause (connection refused, timeout, 5xx, open circuit) is
 * logged instead of assumed — same reasoning as
 * AuthServiceClientFallbackFactory, applied here because a payment/transfer
 * failure is exactly the kind of thing worth diagnosing precisely.
 *
 * Both methods throw WalletServiceUnavailableException rather than
 * returning a graceful "failed" response: a graceful response with
 * success=false is indistinguishable from wallet-service genuinely
 * declining the operation (e.g. insufficient funds), so callers ended up
 * telling users "your wallet may have insufficient funds, please top up"
 * even when the real problem was wallet-service being unreachable —
 * misleading, since topping up wouldn't fix that. Callers catch this
 * specific exception, still record the attempt as FAILED for the audit
 * trail, then re-throw so the user gets the correct "service unavailable"
 * signal.
 *
 * No longer a lambda: WalletServiceClient has two methods now
 * (debitForService, transferFunds), so it's not a single-abstract-method
 * interface anymore.
 */
@Slf4j
@Component
public class WalletServiceClientFallbackFactory implements FallbackFactory<WalletServiceClient> {

    @Override
    public WalletServiceClient create(Throwable cause) {
        return new WalletServiceClient() {
            @Override
            public ApiResponse<Object> debitForService(WalletInternalPaymentRequest request) {
                log.warn("wallet-service call failed, using fallback: debitForService(reference={}) — {}: {}",
                        request.getReference(), cause.getClass().getSimpleName(), cause.getMessage());
                throw new WalletServiceUnavailableException(
                        "Payments are temporarily unavailable. Please try again shortly.");
            }

            @Override
            public ApiResponse<Object> transferFunds(WalletTransferRequest request) {
                log.warn("wallet-service call failed, using fallback: transferFunds(reference={}) — {}: {}",
                        request.getReference(), cause.getClass().getSimpleName(), cause.getMessage());
                throw new WalletServiceUnavailableException(
                        "Payments are temporarily unavailable. Please try again shortly.");
            }
        };
    }
}