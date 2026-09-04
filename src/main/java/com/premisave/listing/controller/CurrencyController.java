package com.premisave.listing.controller;

import com.premisave.listing.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * CurrencyController
 *
 * Public endpoints — no authentication required.
 * The frontend uses these for display-only price conversion while browsing.
 * Actual payment settlement happens directly in USD via wallet-service —
 * no currency conversion occurs on the payment path itself, since
 * wallet-service's canonical currency and this system's canonical currency
 * are both USD.
 *
 * Base: /currency
 */
@Slf4j
@RestController
@RequestMapping("/currency")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyService currencyService;

    /**
     * Get all live rates from the base currency (USD).
     *
     * GET /currency/rates
     *
     * Response: { "base": "USD", "rates": { "KES": "129.4", "EUR": "0.92", ... } }
     *
     * Frontend uses this to display listing prices in the user's preferred currency.
     * Rates are cached in Redis and refreshed every hour — fast response, no API cost per user.
     */
    @GetMapping("/rates")
    public ResponseEntity<Map<String, Object>> getAllRates() {
        Map<Object, Object> rates = currencyService.getAllRates();

        Map<String, Object> response = new HashMap<>();
        response.put("base", CurrencyService.BASE_CURRENCY);
        response.put("rates", rates);

        return ResponseEntity.ok(response);
    }

    /**
     * Get a single exchange rate from the base currency (USD) to a target currency.
     *
     * GET /currency/rate?to=KES
     *
     * Response: { "base": "USD", "target": "KES", "rate": "129.4" }
     */
    @GetMapping("/rate")
    public ResponseEntity<Map<String, Object>> getRate(@RequestParam String to) {
        BigDecimal rate = currencyService.getRate(to.toUpperCase());

        Map<String, Object> response = new HashMap<>();
        response.put("base", CurrencyService.BASE_CURRENCY);
        response.put("target", to.toUpperCase());
        response.put("rate", rate);

        return ResponseEntity.ok(response);
    }

    /**
     * Convert a base-currency (USD) amount to a target currency.
     *
     * GET /currency/convert?amount=2.99&to=KES
     *
     * Response: { "from": "USD", "to": "KES", "originalAmount": 2.99, "convertedAmount": 386.9, "rate": "129.4" }
     *
     * Useful for the frontend to display promotion prices in the user's local currency.
     */
    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String to) {

        String targetCurrency = to.toUpperCase();
        BigDecimal rate = currencyService.getRate(targetCurrency);
        BigDecimal converted = currencyService.convertFromBase(amount, targetCurrency);

        Map<String, Object> response = new HashMap<>();
        response.put("from", CurrencyService.BASE_CURRENCY);
        response.put("to", targetCurrency);
        response.put("originalAmount", amount);
        response.put("convertedAmount", converted);
        response.put("rate", rate);

        return ResponseEntity.ok(response);
    }

    /**
     * Get the list of all supported currency codes.
     *
     * GET /currency/supported
     *
     * Response: { "currencies": ["KES", "EUR", "GBP", "NGN", ...] }
     *
     * Frontend uses this to populate the currency selector dropdown.
     */
    @GetMapping("/supported")
    public ResponseEntity<Map<String, Object>> getSupportedCurrencies() {
        Set<Object> currencies = currencyService.getSupportedCurrencies();

        Map<String, Object> response = new HashMap<>();
        response.put("base", CurrencyService.BASE_CURRENCY);
        response.put("currencies", currencies);
        response.put("count", currencies.size());

        return ResponseEntity.ok(response);
    }
}