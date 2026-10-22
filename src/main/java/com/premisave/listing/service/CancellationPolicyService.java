package com.premisave.listing.service;

import com.premisave.listing.config.CancellationProperties;
import com.premisave.listing.config.CancellationProperties.Tier;
import com.premisave.listing.dto.CancellationQuoteResponse;
import com.premisave.listing.entity.Booking;
import com.premisave.listing.enums.CancellationPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CancellationPolicyService {

    private final CancellationProperties properties;

    /** The listing's own policy, or the configured default when it has none. */
    public CancellationPolicy resolve(CancellationPolicy fromListing) {
        return fromListing != null ? fromListing : properties.getDefaultPolicy();
    }

    /**
     * Percentage of the total the tenant gets back if they cancel at the given
     * time. Owner-initiated cancellations never use this: the tenant always
     * gets everything back.
     */
    public int refundPercent(CancellationPolicy policy, LocalDateTime checkIn, LocalDateTime now) {
        long minutesUntilCheckIn = ChronoUnit.MINUTES.between(now, checkIn);
        if (minutesUntilCheckIn < 0) {
            return 0;
        }
        List<Tier> tiers = new ArrayList<>(properties.getPolicies().get(policy));
        tiers.sort(Comparator.comparingInt(Tier::getHoursBeforeCheckIn).reversed());
        for (Tier tier : tiers) {
            if (minutesUntilCheckIn >= tier.getHoursBeforeCheckIn() * 60L) {
                return tier.getRefundPercent();
            }
        }
        return 0;
    }

    public BigDecimal portion(BigDecimal total, int percent) {
        return total.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public CancellationQuoteResponse quote(Booking booking, boolean cancelledByOwner, LocalDateTime now) {
        CancellationPolicy policy = resolve(booking.getCancellationPolicy());
        int percent = cancelledByOwner ? 100 : refundPercent(policy, booking.getCheckIn(), now);
        BigDecimal refund = portion(booking.getTotalAmount(), percent);
        BigDecimal ownerShare = booking.getTotalAmount().subtract(refund);
        long hoursUntil = ChronoUnit.HOURS.between(now, booking.getCheckIn());

        String message;
        if (cancelledByOwner) {
            message = "The tenant gets a full refund when the owner cancels.";
        } else if (percent == 100) {
            message = "Full refund.";
        } else if (percent == 0) {
            message = "No refund applies under the " + policy + " policy at this point.";
        } else {
            message = percent + "% refund under the " + policy + " policy.";
        }

        return new CancellationQuoteResponse(
                booking.getId(), policy, cancelledByOwner, hoursUntil, percent,
                refund, ownerShare, booking.getCurrency(), message);
    }
}
