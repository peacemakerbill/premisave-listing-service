package com.premisave.listing.util;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.BucketProxy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.function.Supplier;

@Slf4j
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;
    private final JwtUtil jwtUtil;

    @Value("${rate-limit.requests-per-minute:100}")
    private int requestsPerMinute;

    public RateLimiterInterceptor(ProxyManager<String> proxyManager, JwtUtil jwtUtil) {
        this.proxyManager = proxyManager;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String key = resolveRateLimitKey(request);

        @SuppressWarnings("deprecation")
		Supplier<BucketConfiguration> configSupplier = () -> BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(requestsPerMinute, Refill.intervally(requestsPerMinute, Duration.ofMinutes(1))))
                .build();

        BucketProxy bucket = proxyManager.builder().build(key, configSupplier);

        if (bucket.tryConsume(1)) {
            return true;
        }

        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\": \"Too Many Requests\"}");
        return false;
    }

    /**
     * Rate-limits per authenticated user where possible, falling back to
     * per-IP for unauthenticated requests. Replaces the old design where
     * every caller on the instance shared one bucket, so a single busy user
     * (or a scraper) could exhaust the whole instance's budget for everyone
     * else.
     *
     * NOTE: getRemoteAddr() reflects the direct TCP peer, which is your load
     * balancer/reverse proxy if you're behind one. If so, resolve the real
     * client IP from a trusted X-Forwarded-For header instead — only trust
     * that header when the connection itself comes from a known proxy, or
     * it becomes trivially spoofable as a rate-limit bypass.
     */
    private String resolveRateLimitKey(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String userId = jwtUtil.extractUserId(authHeader);
                if (userId != null && !userId.isBlank()) {
                    return "user:" + userId;
                }
            } catch (Exception e) {
                log.debug("Could not extract userId for rate limiting, falling back to IP: {}", e.getMessage());
            }
        }
        return "ip:" + request.getRemoteAddr();
    }
}