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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CurrencyService
 *
 * Wraps the Frankfurter exchange rate API (https://frankfurter.dev) — free,
 * open-source, no API key required, sourced from 84 central banks.
 *
 * All rates are fetched FROM the system's canonical currency (BASE_CURRENCY,
 * currently USD). Method/field names are deliberately currency-agnostic
 * (convertFromBase, not convertFromUsd) — this used to be KES, and renamed
 * everything the first time it changed; naming this generically means the
 * next change (if there is one) only touches BASE_CURRENCY's value, not
 * every method/field name that mentions a specific currency.
 *
 * NOTE: The legacy Frankfurter v1 API only covers ~30 major (mostly ECB)
 * currencies. This service uses the v2 API
 * (https://api.frankfurter.dev/v2), which covers 200+ currencies.
 *
 * Caching strategy:
 *   - Full rate table cached in Redis for 1 hour under a key derived from
 *     BASE_CURRENCY (e.g. "fx:rates:USD")
 *   - Individual pair lookups read from that cached map — no extra API calls
 *   - On cache miss, the full table is refreshed in one API call
 *   - This means regardless of how many users are converting currencies
 *     simultaneously, the system makes at most 1 API call per hour
 *
 * Frankfurter v2 API:
 *   GET https://api.frankfurter.dev/v2/rates?base=USD
 *   Response: a JSON array of { "date": "...", "base": "USD", "quote": "KES", "rate": 129.4 }
 *   one entry per supported currency (no API key needed).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyService {

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${frankfurter.base-url:https://api.frankfurter.dev}")
    private String baseUrl;

    /** Canonical system currency — all amounts stored/settled in this
     *  currency on the backend. */
    public static final String BASE_CURRENCY = "USD";

    private static final String REDIS_KEY = "fx:rates:" + BASE_CURRENCY;
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    // ====================== PUBLIC API ======================

    /**
     * Convert an amount from the base currency to the target currency.
     *
     * @param amountBase      amount in BASE_CURRENCY (the canonical backend currency)
     * @param targetCurrency  ISO 4217 currency code e.g. "KES", "EUR", "NGN"
     * @return converted amount, rounded to 2 decimal places
     */
    public BigDecimal convertFromBase(BigDecimal amountBase, String targetCurrency) {
        if (targetCurrency == null || targetCurrency.isBlank()
                || targetCurrency.equalsIgnoreCase(BASE_CURRENCY)) {
            return amountBase.setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal rate = getRate(targetCurrency.toUpperCase());
        BigDecimal converted = amountBase.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        log.debug("Converted {} {} → {} {} (rate: {})", amountBase, BASE_CURRENCY, converted, targetCurrency, rate);
        return converted;
    }

    /**
     * Convert an amount from a foreign currency back to the base currency.
     * Used when a payment arrives in a foreign currency and we need the
     * base-currency equivalent for records.
     *
     * @param amount          amount in the source currency
     * @param sourceCurrency  ISO 4217 currency code
     * @return equivalent amount in BASE_CURRENCY
     */
    public BigDecimal convertToBase(BigDecimal amount, String sourceCurrency) {
        if (sourceCurrency == null || sourceCurrency.isBlank()
                || sourceCurrency.equalsIgnoreCase(BASE_CURRENCY)) {
            return amount.setScale(2, RoundingMode.HALF_UP);
        }

        // Rate is BASE per 1 unit of source currency = 1 / (BASE→source rate)
        BigDecimal basePerUnit = getRate(sourceCurrency.toUpperCase());
        if (basePerUnit.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("Invalid exchange rate (zero) for currency: " + sourceCurrency);
        }

        BigDecimal amountInBase = amount.divide(basePerUnit, 10, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Converted {} {} → {} {} (rate: {})", amount, sourceCurrency, amountInBase, BASE_CURRENCY, basePerUnit);
        return amountInBase;
    }

    /**
     * Get the live exchange rate: 1 BASE_CURRENCY = ? targetCurrency
     *
     * @param targetCurrency ISO 4217 code
     * @return rate as BigDecimal
     */
    public BigDecimal getRate(String targetCurrency) {
        String upper = targetCurrency.toUpperCase();

        // BASE → BASE is always 1, and Frankfurter never returns the base as one of the quotes
        if (upper.equals(BASE_CURRENCY)) {
            return BigDecimal.ONE;
        }

        // 1. Try Redis cache
        String cached = redisTemplate.opsForHash().get(REDIS_KEY, upper) != null
                ? (String) redisTemplate.opsForHash().get(REDIS_KEY, upper)
                : null;

        if (cached != null) {
            log.debug("Cache HIT for {}/{} rate: {}", upper, BASE_CURRENCY, cached);
            return new BigDecimal(cached);
        }

        // 2. Cache miss — refresh full rate table
        log.info("Cache MISS for {}. Fetching full rate table from Frankfurter.", upper);
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
     * Returns the full rate map: { "KES": "129.4", "EUR": "0.92", ... }
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
     * Fetches the full base-currency rate table from Frankfurter and stores
     * it in Redis. Called on cache miss. Can also be called by a scheduler
     * to pre-warm the cache.
     *
     * Frankfurter's /v2/rates endpoint returns a JSON array, one row per quote
     * currency, e.g.:
     *   [{"date":"2026-06-19","base":"USD","quote":"KES","rate":129.4}, ...]
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void refreshRates() {
        String url = baseUrl + "/v2/rates?base=" + BASE_CURRENCY;

        try {
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);

            List<Map<String, Object>> body = response.getBody();
            if (body == null || body.isEmpty()) {
                log.error("Frankfurter returned an empty rates list for base={}", BASE_CURRENCY);
                return;
            }

            Map<String, String> rateStrings = new HashMap<>();
            for (Map<String, Object> row : body) {
                Object quoteObj = row.get("quote");
                Object rateObj = row.get("rate");
                if (quoteObj == null || rateObj == null) {
                    continue;
                }
                rateStrings.put(quoteObj.toString(), rateObj.toString());
            }

            if (rateStrings.isEmpty()) {
                log.error("Frankfurter response had no usable quote/rate entries: {}", body);
                return;
            }

            // Atomic replace: delete old hash, write new one, set TTL
            redisTemplate.delete(REDIS_KEY);
            redisTemplate.opsForHash().putAll(REDIS_KEY, rateStrings);
            redisTemplate.expire(REDIS_KEY, CACHE_TTL);

            log.info("Exchange rates refreshed: {} currencies cached from Frankfurter (base={}).",
                    rateStrings.size(), BASE_CURRENCY);

        } catch (Exception e) {
            log.error("Failed to refresh exchange rates from Frankfurter: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            // Do not rethrow — stale cache is better than a crash
        }
    }
}