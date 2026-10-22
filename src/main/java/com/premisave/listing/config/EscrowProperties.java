package com.premisave.listing.config;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the escrow.* section in application.yml.
 *
 * With escrow on, a booking payment is held by wallet-service in an escrow
 * record (POST /internal/escrow/hold) and paid on to the owner after check-in,
 * or refunded, with escrow payouts. No holding wallet or account is needed:
 * wallet-service owns the held money and charges its commission once, when
 * the tenant pays.
 */
@Data
@Component
@ConfigurationProperties(prefix = "escrow")
public class EscrowProperties implements InitializingBean {

    private boolean enabled = false;

    /** Hours after check-in before the owner is paid. */
    private int releaseDelayHours = 24;

    /** Payout attempts before a booking is parked as NEEDS_REVIEW. */
    private int maxAttempts = 5;

    /** How long one instance may work on a booking before another can pick it up. */
    private int leaseMinutes = 5;

    /** Wait before a failed payout is retried, multiplied by the attempt number. */
    private int retryBackoffMinutes = 2;

    /** Bookings handled per job run. */
    private int batchSize = 100;

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        if (releaseDelayHours < 0 || maxAttempts < 1 || leaseMinutes < 1 || retryBackoffMinutes < 1 || batchSize < 1) {
            throw new IllegalStateException("escrow.* contains an out-of-range value.");
        }
    }
}
