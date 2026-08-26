package com.premisave.listing.client;

import com.premisave.listing.config.WalletFeignConfig;
import com.premisave.listing.dto.wallet_service.WalletInternalPaymentRequest;
import com.premisave.listing.dto.wallet_service.WalletPaymentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Client for wallet-service's internal, service-to-service payment
 * endpoint. Authenticated via a static X-API-Key (WalletApiKeyFeignInterceptor),
 * not JWT propagation — see WalletFeignConfig.
 *
 * spring.cloud.openfeign.circuitbreaker.enabled=true (application.yml) turns
 * on the fallback below when wallet-service is slow/unreachable, so a
 * dependency outage here fails a payment cleanly instead of hanging the
 * caller's thread or throwing an unhandled exception.
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
    WalletPaymentResponse debitForService(@RequestBody WalletInternalPaymentRequest request);
}