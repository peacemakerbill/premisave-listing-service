package com.premisave.listing.config;

import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Per-client Feign configuration for WalletServiceClient — kept separate
 * from FeignConfig (used by AuthServiceClient) because the two clients
 * authenticate completely differently: auth-service gets the caller's
 * forwarded JWT, wallet-service's internal endpoint gets a static
 * service-to-service API key.
 */
@Configuration
public class WalletFeignConfig {

    @Bean
    public RequestInterceptor walletApiKeyRequestInterceptor(WalletApiKeyFeignInterceptor interceptor) {
        return interceptor;
    }
}