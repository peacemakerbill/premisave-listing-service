package com.premisave.listing.config;

import com.premisave.listing.enums.CancellationPolicy;
import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Binds booking.cancellation.* in application.yml. Each policy is a list of
 * tiers: if the tenant cancels at least hoursBeforeCheckIn hours ahead, they
 * get refundPercent back. The best matching tier wins; if none match (or the
 * stay has already started) the refund is 0%.
 */
@Data
@Component
@ConfigurationProperties(prefix = "booking.cancellation")
public class CancellationProperties implements InitializingBean {

    /** Used when a listing has no policy of its own. */
    private CancellationPolicy defaultPolicy = CancellationPolicy.MODERATE;

    private Map<CancellationPolicy, List<Tier>> policies = new EnumMap<>(CancellationPolicy.class);

    @Data
    public static class Tier {
        private int hoursBeforeCheckIn;
        private int refundPercent;
    }

    @Override
    public void afterPropertiesSet() {
        for (CancellationPolicy policy : CancellationPolicy.values()) {
            List<Tier> tiers = policies.get(policy);
            if (tiers == null || tiers.isEmpty()) {
                throw new IllegalStateException(
                        "booking.cancellation.policies." + policy + " is missing in application.yml.");
            }
            for (Tier tier : tiers) {
                if (tier.getHoursBeforeCheckIn() < 0 || tier.getRefundPercent() < 0 || tier.getRefundPercent() > 100) {
                    throw new IllegalStateException(
                            "booking.cancellation.policies." + policy + " has a tier with an out-of-range value.");
                }
            }
        }
    }
}
