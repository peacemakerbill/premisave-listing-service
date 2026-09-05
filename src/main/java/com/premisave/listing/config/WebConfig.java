package com.premisave.listing.config;

import com.premisave.listing.util.RateLimiterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * pageSerializationMode = VIA_DTO wraps every Page<T> response (every
 * endpoint returning one — admin listings, promotions, bookings,
 * interests) in Spring Data's PagedModel before serializing it, instead of
 * serializing PageImpl directly. Spring Data's own Jackson module logs a
 * warning on every single paginated response without this: "Serializing
 * PageImpl instances as-is is not supported, meaning there is no guarantee
 * about the stability of the resulting JSON structure" — direct
 * serialization can silently change shape across Spring Data versions,
 * since PageImpl was never designed as a wire format.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
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
                    "/system/health",
                    "/system/health/details",
                    "/swagger-ui/**",
                    "/v3/api-docs/**"
                );
    }
}