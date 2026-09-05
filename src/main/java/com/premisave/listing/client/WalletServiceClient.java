package com.premisave.listing.client;

import com.premisave.listing.config.WalletFeignConfig;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import org.springframework.cloud.openfeign.FeignClient;
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
}