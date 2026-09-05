package com.premisave.listing.util;

import com.premisave.listing.config.RateLimitProperties;
import com.premisave.listing.config.RateLimiterConfig;
import com.premisave.listing.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Per-identity, per-tier, Redis-backed rate limiting. Identity is the
 * authenticated userId when a valid Authorization header is present,
 * otherwise the caller's IP — so an anonymous flood against a public
 * endpoint (browsing, search) is still rate-limited per source, not
 * lumped into one shared budget with everyone else.
 *
 * Tier is resolved by matching "METHOD:ant-pattern" entries in
 * rate-limit.sensitive-paths / rate-limit.write-paths (checked in that
 * order, first match wins); anything unmatched falls into the default
 * tier. Each tier is tracked as an independent Redis key per identity, so
 * exhausting the sensitive-tier budget (bookings, promotions, interests)
 * doesn't affect the caller's ability to keep browsing.
 */
@Slf4j
@Component
public class RateLimiterInterceptor implements HandlerInterceptor {

    private final ProxyManager<String> proxyManager;
    private final RateLimitProperties properties;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final Supplier<BucketConfiguration> defaultConfig;
    private final Supplier<BucketConfiguration> writeConfig;
    private final Supplier<BucketConfiguration> sensitiveConfig;

    public RateLimiterInterceptor(ProxyManager<String> proxyManager,
                                   RateLimitProperties properties,
                                   JwtUtil jwtUtil,
                                   ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
        this.properties = properties;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
        // Configurations only need building once — Bandwidth/BucketConfiguration
        // are immutable, so these are cached rather than rebuilt per request.
        this.defaultConfig = () -> RateLimiterConfig.toBucketConfiguration(properties.getDefaultTier());
        this.writeConfig = () -> RateLimiterConfig.toBucketConfiguration(properties.getWriteTier());
        this.sensitiveConfig = () -> RateLimiterConfig.toBucketConfiguration(properties.getSensitiveTier());
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!properties.isEnabled()) {
            return true;
        }

        String tierName = resolveTier(request);
        Supplier<BucketConfiguration> config = switch (tierName) {
            case "sensitive" -> sensitiveConfig;
            case "write" -> writeConfig;
            default -> defaultConfig;
        };

        String identity = resolveIdentity(request);
        String key = "ratelimit:" + tierName + ":" + identity;

        ConsumptionProbe probe = proxyManager.builder().build(key, config).tryConsumeAndReturnRemaining(1);

        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            return true;
        }

        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setStatus(429);
        response.setContentType("application/json");

        ApiResponse<String> body = new ApiResponse<>(
                false,
                "Too many requests. Please try again in " + retryAfterSeconds + " second(s).",
                null);
        response.getWriter().write(objectMapper.writeValueAsString(body));

        log.warn("Rate limit exceeded: tier={}, identity={}, path={}", tierName, identity, request.getRequestURI());
        return false;
    }

    private String resolveTier(HttpServletRequest request) {
        if (matchesAny(request, properties.getSensitivePaths())) {
            return "sensitive";
        }
        if (matchesAny(request, properties.getWritePaths())) {
            return "write";
        }
        return "default";
    }

    private boolean matchesAny(HttpServletRequest request, List<String> patterns) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        for (String entry : patterns) {
            int separator = entry.indexOf(':');
            if (separator < 0) continue;
            String entryMethod = entry.substring(0, separator);
            String entryPattern = entry.substring(separator + 1);
            if (entryMethod.equalsIgnoreCase(method) && pathMatcher.match(entryPattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * userId when a valid, currently-parseable token is present, otherwise
     * the caller's IP. Deliberately never throws — a malformed or expired
     * token here just means "treat this caller as anonymous for rate-
     * limiting purposes," not a reason to fail the request; JwtAuthenticationFilter
     * (which already ran, since interceptors execute after Security's
     * filter chain) is the actual authority on whether the request is
     * authorized at all.
     */
    private String resolveIdentity(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            try {
                String userId = jwtUtil.extractUserId(authHeader);
                if (userId != null && !userId.isBlank()) {
                    return "user:" + userId;
                }
            } catch (Exception e) {
                // Falls through to IP-based identity below.
            }
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        // X-Forwarded-For is only trustworthy behind a reverse proxy/load
        // balancer that sets it itself (overwriting any client-supplied
        // value) — true in this deployment, but worth remembering if this
        // service is ever exposed directly to the internet without one,
        // since a client could otherwise spoof this header to evade limits.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}