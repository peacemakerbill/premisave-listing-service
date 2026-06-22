package com.premisave.listing.config;

import com.premisave.listing.util.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimiterInterceptor rateLimiterInterceptor;

    public WebConfig(RateLimiterInterceptor rateLimiterInterceptor) {
        this.rateLimiterInterceptor = rateLimiterInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimiterInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                    "/public/**",
                    "/health",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    // M-Pesa callbacks come from Safaricom servers — exclude from rate limiting
                    // so retries are never dropped
                    "/payments/mpesa/callback",
                    "/payments/mpesa/confirmation",
                    "/payments/mpesa/validation",
                    "/payments/mpesa/c2b/status"
                );
    }
}