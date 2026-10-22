package com.premisave.listing.client;

import com.premisave.listing.config.WalletFeignConfig;
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
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client for wallet-service's internal, service-to-service endpoints.
 * Authenticated via a static X-API-Key (WalletApiKeyFeignInterceptor), not
 * JWT propagation — see WalletFeignConfig.
 *
 * Both methods return ApiResponse<Object> — the confirmed wrapper shape
 * ({success, message, data}), same class already used for this service's
 * own API responses. The "data" payload (PaymentResponse fields) was never
 * fully confirmed, so it's left as an opaque Object here rather than
 * guessed at field-by-field — everything this service's own logic actually
 * needs (isSuccess/getMessage) is on the wrapper itself, not inside data.
 *
 * spring.cloud.openfeign.circuitbreaker.enabled=true (application.yml) turns
 * on the fallback (WalletServiceClientFallbackFactory) when wallet-service
 * is slow/unreachable, so a dependency outage here fails cleanly instead of
 * hanging the caller's thread or throwing an unhandled exception.
 */
@FeignClient(
    name = "wallet-service",
    url = "${wallet.service.url:http://localhost:8084}",
    configuration = WalletFeignConfig.class,
    fallbackFactory = WalletServiceClientFallbackFactory.class
)
public interface WalletServiceClient {

    /**
     * Debits the given user's wallet for a service charge (e.g. an ad
     * promotion). Expected to be synchronous — resolving an existing
     * wallet balance is a different operation from funding one via an
     * external gateway (M-Pesa/PayPal/etc.), which is inherently async and
     * is entirely wallet-service's own concern now.
     */
    @PostMapping("/internal/payment")
    ApiResponse<Object> debitForService(@RequestBody WalletInternalPaymentRequest request);

    /**
     * Moves money directly between two wallet accounts — used for booking
     * payments (tenant -> owner) and cancellation refunds (owner ->
     * tenant). See WalletTransferRequest for the recipientAccountNumber
     * assumption (resolved as the recipient's email).
     */
    @PostMapping("/internal/transfer")
    ApiResponse<Object> transferFunds(@RequestBody WalletTransferRequest request);

    /**
     * Looks up a transfer by the reference it was made with. 404 means no
     * transfer was recorded (yet). A record exists only after the balances
     * moved, so finding one means the money moved.
     */
    @GetMapping("/internal/transfer/{reference}")
    ApiResponse<WalletTransferRecord> getTransfer(@PathVariable String reference);

    /** Current commission rates. The sender pays the rate on top of the amount. */
    @GetMapping("/internal/commission/rates")
    ApiResponse<WalletCommissionRates> getCommissionRates();

    /**
     * Escrow: debits the tenant once (price plus wallet-service's commission) and keeps the price in an
     * escrow record. 409 means the reference is already used (check it with getEscrow).
     */
    @PostMapping("/internal/escrow/hold")
    ApiResponse<EscrowHoldResponse> holdEscrow(@RequestBody EscrowHoldRequest request);

    /** Escrow: pays part or all of what is left in a hold to the tenant or the owner. No commission. */
    @PostMapping("/internal/escrow/{holdReference}/payouts")
    ApiResponse<EscrowPayoutResponse> payoutEscrow(@PathVariable String holdReference,
                                                   @RequestBody EscrowPayoutRequest request);

    /** Escrow: the hold and all its payouts. 404 means there is no such hold. */
    @GetMapping("/internal/escrow/{holdReference}")
    ApiResponse<EscrowDetails> getEscrow(@PathVariable String holdReference);
}
