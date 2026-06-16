package com.premisave.listing.dto;

import com.premisave.listing.enums.SubscriptionPlan;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class SubscriptionDto {
    private String id;
    private String ownerId;
    private SubscriptionPlan plan;
    private BigDecimal amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active;
}