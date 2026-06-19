package com.premisave.listing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * CurrencyService
 *
 * Wraps the FastForex real-time exchange rate API.
 * All rates are fetched FROM KES (the system's canonical currency).
 *
 * Caching strategy:
 *   - Full rate table cached in Redis for 1 hour under key "fx:rates:KES"
 *   - Individual pair lookups read from that cached map — no extra API calls
 *   - On cache miss, the full table is refreshed in one API call
 *   - This means regardless of how many users are converting currencies
 *     simultaneously, the system makes at most 1 API call per hour
 *
 * FastForex API:
 *   GET https://api.fastforex.io/fetch-all?from=KES&api_key={key}
 *   Response: { "base": "KES", "results": { "USD": 0.00775, "EUR": 0.00712, ... }, "updated": "...", "ms": 3 }
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${fastforex.api-key}")
    private String apiKey;

    @Value("${fastforex.base-url:https://api.fastforex.io}")
    private String baseUrl;

    /** Canonical system currency — all amounts stored in KES on the backend */
    public static final String BASE_CURRENCY = "KES";

    private static final String REDIS_KEY = "fx:rates:KES";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // ====================== PUBLIC API ======================

    /**
     * Convert an amount from KES to the target currency.
     *
     * @param amountKes   amount in KES (the canonical backend currency)
     * @param targetCurrency  ISO 4217 currency code e.g. "USD", "EUR", "NGN"
     * @return converted amount, rounded to 2 decimal places
     */
    public BigDecimal convertFromKes(BigDecimal amountKes, String targetCurrency) {
        if (targetCurrency == null || targetCurrency.isBlank()
                || targetCurrency.equalsIgnoreCase(BASE_CURRENCY)) {
            return amountKes.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rate = getRate(targetCurrency.toUpperCase());
        BigDecimal converted = amountKes.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        log.debug("Converted {} KES → {} {} (rate: {})", amountKes, converted, targetCurrency, rate);
        return converted;
    }

    /**
     * Convert an amount from a foreign currency back to KES.
     * Used when a payment arrives in a foreign currency and we need the KES equivalent for records.
     *
     * @param amount          amount in the source currency
     * @param sourceCurrency  ISO 4217 currency code
     * @return equivalent amount in KES
     */
    public BigDecimal convertToKes(BigDecimal amount, String sourceCurrency) {
        if (sourceCurrency == null || sourceCurrency.isBlank()
                || sourceCurrency.equalsIgnoreCase(BASE_CURRENCY)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        // Rate is KES per 1 unit of source currency = 1 / (KES→source rate)
        BigDecimal kesPerUnit = getRate(sourceCurrency.toUpperCase());
        if (kesPerUnit.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Invalid exchange rate (zero) for currency: " + sourceCurrency);
        }

        BigDecimal amountInKes = amount.divide(kesPerUnit, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Converted {} {} → {} KES (rate: {})", amount, sourceCurrency, amountInKes, kesPerUnit);
        return amountInKes;
    }

    /**
     * Get the live exchange rate: 1 KES = ? targetCurrency
     *
     * @param targetCurrency ISO 4217 code
     * @return rate as BigDecimal
     */
    public BigDecimal getRate(String targetCurrency) {
        String upper = targetCurrency.toUpperCase();

        // 1. Try Redis cache
        String cached = redisTemplate.opsForHash().get(REDIS_KEY, upper) != null
                ? (String) redisTemplate.opsForHash().get(REDIS_KEY, upper)
                : null;

        if (cached != null) {
            log.debug("Cache HIT for {}/KES rate: {}", upper, cached);
            return new BigDecimal(cached);
        }

        // 2. Cache miss — refresh full rate table
        log.info("Cache MISS for {}. Fetching full rate table from FastForex.", upper);
        refreshRates();

        // 3. Try again after refresh
        String refreshed = (String) redisTemplate.opsForHash().get(REDIS_KEY, upper);
        if (refreshed == null) {
            throw new RuntimeException(
                "Currency not supported or not found after rate refresh: " + upper +
                ". Please use a valid ISO 4217 currency code."
            );
        }

        return new BigDecimal(refreshed);
    }

    /**
     * Returns all supported currency codes.
     * Reads from cache if available, fetches from API otherwise.
     */
    public Set<Object> getSupportedCurrencies() {
        // Ensure cache is populated
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_KEY))) {
            refreshRates();
        }
        return redisTemplate.opsForHash().keys(REDIS_KEY);
    }

    /**
     * Returns the full rate map: { "USD": "0.00775", "EUR": "0.00712", ... }
     * Always reads from cache; refreshes if stale.
     */
    public Map<Object, Object> getAllRates() {
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_KEY))) {
            refreshRates();
        }
        return redisTemplate.opsForHash().entries(REDIS_KEY);
    }

    // ====================== CACHE REFRESH ======================

    /**
     * Fetches the full KES-based rate table from FastForex and stores it in Redis.
     * Called on cache miss. Can also be called by a scheduler to pre-warm the cache.
     */
    @SuppressWarnings("unchecked")
    public void refreshRates() {
        String url = baseUrl + "/fetch-all?from=" + BASE_CURRENCY + "&api_key=" + apiKey;

        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getBody() == null) {
                log.error("FastForex returned empty response body.");
                return;
            }

            Map<String, Object> body = response.getBody();
            Map<String, Object> results = (Map<String, Object>) body.get("results");

            if (results == null || results.isEmpty()) {
                log.error("FastForex response missing 'results' field: {}", body);
                return;
            }

            // Store each rate as a string in the Redis hash
            Map<String, String> rateStrings = new java.util.HashMap<>();
            results.forEach((currency, rate) ->
                rateStrings.put(currency, rate.toString())
            );

            // Atomic replace: delete old hash, write new one, set TTL
            redisTemplate.delete(REDIS_KEY);
            redisTemplate.opsForHash().putAll(REDIS_KEY, rateStrings);
            redisTemplate.expire(REDIS_KEY, CACHE_TTL);

            String updated = (String) body.getOrDefault("updated", "unknown");
            log.info("Exchange rates refreshed: {} currencies cached. FastForex updated at: {}",
                    rateStrings.size(), updated);

        } catch (Exception e) {
            log.error("Failed to refresh exchange rates from FastForex: {}", e.getMessage(), e);
            // Do not rethrow — stale cache is better than a crash
        }
    }
}