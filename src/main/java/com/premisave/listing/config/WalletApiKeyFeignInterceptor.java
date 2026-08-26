package com.premisave.listing.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Attaches the static X-API-Key header wallet-service expects on its
 * internal, service-to-service endpoints (POST /internal/payment).
 *
 * This is deliberately NOT the JWT-forwarding pattern used for auth-service
 * (JwtFeignInterceptor) — wallet-service's internal endpoints authenticate
 * the *calling service*, not the end user, so listing-service identifies
 * itself with its own credential rather than passing along the user's
 * Bearer token.
 */
@Component
public class WalletApiKeyFeignInterceptor implements RequestInterceptor {

    @Value("${wallet.internal.api-key}")
    private String apiKey;

    @Override
    public void apply(RequestTemplate template) {
        template.header("X-API-Key", apiKey);
    }
}