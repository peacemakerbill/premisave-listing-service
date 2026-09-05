package com.premisave.listing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the rate-limit.* section in application.yml. Three tiers, each
 * independently configurable and each tracked as a SEPARATE bucket per
 * identity — so a user hammering /bookings doesn't burn through the same
 * budget as their ordinary browsing, and vice versa.
 *
 * sensitivePaths/writePaths entries are "METHOD:ant-pattern", e.g.
 * "POST:/bookings/*&#47;cancel" — checked in order, first match wins,
 * anything unmatched falls into the default tier.
 */
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /** Global kill switch — false disables rate limiting entirely (e.g. for
     *  local dev without Redis running). */
    private boolean enabled = true;

    private Tier defaultTier = new Tier(300, 300, 60);
    private Tier writeTier = new Tier(30, 30, 60);
    private Tier sensitiveTier = new Tier(10, 10, 60);

    private List<String> sensitivePaths = List.of();
    private List<String> writePaths = List.of();

    public static class Tier {
        private int capacity;
        private int refillTokens;
        private int refillPeriodSeconds;

        public Tier() {}

        public Tier(int capacity, int refillTokens, int refillPeriodSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodSeconds = refillPeriodSeconds;
        }

        public int getCapacity() { return capacity; }
        public void setCapacity(int capacity) { this.capacity = capacity; }
        public int getRefillTokens() { return refillTokens; }
        public void setRefillTokens(int refillTokens) { this.refillTokens = refillTokens; }
        public int getRefillPeriodSeconds() { return refillPeriodSeconds; }
        public void setRefillPeriodSeconds(int refillPeriodSeconds) { this.refillPeriodSeconds = refillPeriodSeconds; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Tier getDefaultTier() { return defaultTier; }
    public void setDefaultTier(Tier defaultTier) { this.defaultTier = defaultTier; }
    public Tier getWriteTier() { return writeTier; }
    public void setWriteTier(Tier writeTier) { this.writeTier = writeTier; }
    public Tier getSensitiveTier() { return sensitiveTier; }
    public void setSensitiveTier(Tier sensitiveTier) { this.sensitiveTier = sensitiveTier; }
    public List<String> getSensitivePaths() { return sensitivePaths; }
    public void setSensitivePaths(List<String> sensitivePaths) { this.sensitivePaths = sensitivePaths; }
    public List<String> getWritePaths() { return writePaths; }
    public void setWritePaths(List<String> writePaths) { this.writePaths = writePaths; }
}