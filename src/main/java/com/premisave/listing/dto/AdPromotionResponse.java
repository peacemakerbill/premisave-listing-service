package com.premisave.listing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdPromotionResponse {
    private String promotionId;
    private String listingId;
    private int days;
    private BigDecimal totalAmount;
    private LocalDateTime endDate;
    private String message;
    private boolean success = true;
}