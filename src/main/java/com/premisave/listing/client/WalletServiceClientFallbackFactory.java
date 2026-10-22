package com.premisave.listing.client;

import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.EscrowDetails;
import com.premisave.listing.dto.wallet_service.EscrowHoldRequest;
import com.premisave.listing.dto.wallet_service.EscrowHoldResponse;
import com.premisave.listing.dto.wallet_service.EscrowPayoutRequest;
import com.premisave.listing.dto.wallet_service.EscrowPayoutResponse;
import com.premisave.listing.dto.wallet_service.WalletCommissionRates;
import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.dto.wallet_service.WalletTransferRecord;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Runs when a wallet-service call fails. Two kinds of failure are told apart:
 *
 * - wallet-service ANSWERED with a 4xx (409 duplicate reference, 422
 *   insufficient funds, 404 not found, 403 frozen, 400 bad request). That is
 *   a real answer, not an outage, so the exception is passed on unchanged
 *   and the caller decides what it means. Before, every failure here became
 *   "temporarily unavailable", which hid declines and duplicates.
 * - anything else (connection refused, timeout, 5xx, open circuit) becomes
 *   WalletServiceUnavailableException, mapped to 503.
 */
@Slf4j
@Component
public class WalletServiceClientFallbackFactory implements FallbackFactory<WalletServiceClient> {

    private static final String UNAVAILABLE = "Payments are temporarily unavailable. Please try again shortly.";

    @Override
    public WalletServiceClient create(Throwable cause) {
        return new WalletServiceClient() {
            @Override
            public ApiResponse<Object> debitForService(WalletInternalPaymentRequest request) {
                throw translate("debitForService(reference=" + request.getReference() + ")", cause);
            }

            @Override
            public ApiResponse<Object> transferFunds(WalletTransferRequest request) {
                throw translate("transferFunds(reference=" + request.getReference() + ")", cause);
            }

            @Override
            public ApiResponse<WalletTransferRecord> getTransfer(String reference) {
                throw translate("getTransfer(reference=" + reference + ")", cause);
            }

            @Override
            public ApiResponse<WalletCommissionRates> getCommissionRates() {
                throw translate("getCommissionRates()", cause);
            }

            @Override
            public ApiResponse<EscrowHoldResponse> holdEscrow(EscrowHoldRequest request) {
                throw translate("holdEscrow(reference=" + request.getReference() + ")", cause);
            }

            @Override
            public ApiResponse<EscrowPayoutResponse> payoutEscrow(String holdReference, EscrowPayoutRequest request) {
                throw translate("payoutEscrow(hold=" + holdReference + ", reference=" + request.getReference() + ")", cause);
            }

            @Override
            public ApiResponse<EscrowDetails> getEscrow(String holdReference) {
                throw translate("getEscrow(hold=" + holdReference + ")", cause);
            }
        };
    }

    private static RuntimeException translate(String call, Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            if (t instanceof FeignException.FeignClientException clientError) {
                return clientError;
            }
        }
        log.warn("wallet-service call failed, using fallback: {} - {}: {}",
                call, cause.getClass().getSimpleName(), cause.getMessage());
        return new WalletServiceUnavailableException(UNAVAILABLE);
    }
}
