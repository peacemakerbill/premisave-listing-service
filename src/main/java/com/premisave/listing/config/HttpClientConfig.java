package com.premisave.listing.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * General-purpose HTTP client configuration.
 *
 * Replaces the old MpesaConfig. M-Pesa integration has moved entirely to
 * wallet-service, but CurrencyService (Frankfurter calls) still needs a
 * RestTemplate bean — it now lives here, with explicit connect/read
 * timeouts instead of the previous unbounded default RestTemplate, which
 * could otherwise hold a request thread indefinitely on a slow upstream.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}