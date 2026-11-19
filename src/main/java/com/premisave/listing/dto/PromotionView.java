package com.premisave.listing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A promotion as the owner reads it: the listing's title and the promotion's photo come with it, and the state is one
 * word: ACTIVE, EXPIRED, PENDING or FAILED.
 */
public record PromotionView(
        String id,
        String listingId,
        String listingTitle,
        String imageUrl,
        String headline,
        int days,
        BigDecimal dailyRate,
        BigDecimal totalAmount,
        String currency,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        LocalDateTime createdAt) {
}
