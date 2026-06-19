package com.premisave.listing.scheduler;

import com.premisave.listing.service.CurrencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CurrencyScheduler
 *
 * Pre-warms the Redis exchange rate cache every hour so that
 * no user request ever has to wait for a live FastForex API call.
 *
 * Without this, the first request after cache expiry would trigger
 * a live API call and experience slightly higher latency.
 * With this scheduler, the cache is always hot.
 *
 * Also runs once on startup (initialDelay = 0) so the cache
 * is populated immediately when the service starts — no cold start lag.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrencyScheduler {

    private final CurrencyService currencyService;

    /**
     * Refresh exchange rates every hour.
     * initialDelay = 0 → runs immediately on application startup.
     * fixedDelay = 3600000ms (1 hour) → runs every hour thereafter.
     */
    @Scheduled(initialDelay = 0, fixedDelay = 3_600_000)
    public void refreshExchangeRates() {
        log.info("Scheduled: refreshing exchange rates from FastForex...");
        try {
            currencyService.refreshRates();
            log.info("Scheduled: exchange rate refresh complete.");
        } catch (Exception e) {
            log.error("Scheduled: exchange rate refresh failed: {}", e.getMessage(), e);
            // Non-fatal — stale cached rates are still served until next successful refresh
        }
    }
}