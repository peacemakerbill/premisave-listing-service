package com.premisave.listing.client;

import com.premisave.listing.dto.wallet_service.WalletPaymentResponse;
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
 * Fails closed: if wallet-service is unreachable for any reason, no
 * payment is ever reported as successful — PaymentService treats this as a
 * failed charge, and AdPromotionService never activates a listing on the
 * strength of an unconfirmed debit.
 */
@Slf4j
@Component
public class WalletServiceClientFallbackFactory implements FallbackFactory<WalletServiceClient> {

    @Override
    public WalletServiceClient create(Throwable cause) {
        return request -> {
            log.warn("wallet-service call failed, using fallback: debitForService(reference={}) — {}: {}",
                    request.getReference(), cause.getClass().getSimpleName(), cause.getMessage());

            WalletPaymentResponse response = new WalletPaymentResponse();
            response.setSuccess(false);
            response.setStatus("SERVICE_UNAVAILABLE");
            response.setMessage("Payments are temporarily unavailable. Please try again shortly.");
            return response;
        };
    }
}