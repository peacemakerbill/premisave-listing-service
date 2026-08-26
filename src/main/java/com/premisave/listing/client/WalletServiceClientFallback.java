package com.premisave.listing.client;

import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.dto.wallet_service.WalletPaymentResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Circuit-breaker fallback for WalletServiceClient. Fails closed: if
 * wallet-service is unreachable or the circuit is open, this never reports
 * a payment as successful — PaymentService's normal failure handling picks
 * this up as PaymentStatus.FAILED, which stops AdPromotionService from ever
 * activating a listing on the strength of an unconfirmed debit.
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public WalletPaymentResponse debitForService(WalletInternalPaymentRequest request) {
        log.error("wallet-service unavailable (circuit open) — could not process payment reference={}",
                request.getReference());

        WalletPaymentResponse response = new WalletPaymentResponse();
        response.setSuccess(false);
        response.setStatus("SERVICE_UNAVAILABLE");
        response.setMessage("Payments are temporarily unavailable. Please try again shortly.");
        return response;
    }
}