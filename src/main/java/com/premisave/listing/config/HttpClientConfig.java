package com.premisave.listing.config;

import org.springframework.boot.restclient.RestTemplateBuilder;
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
 *
 * RestTemplateBuilder's import changed for Spring Boot 4: it moved from
 * org.springframework.boot.web.client to org.springframework.boot.restclient,
 * and its auto-configuration now lives in a separate starter
 * (spring-boot-starter-restclient) rather than being pulled in
 * automatically — see pom.xml.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }
}