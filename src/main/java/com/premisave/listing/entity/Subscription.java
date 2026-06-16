package com.premisave.listing.entity;

import com.premisave.listing.enums.SubscriptionPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "subscriptions")
public class Subscription extends BaseEntity {

    private String ownerId;
    private SubscriptionPlan plan;
    private BigDecimal amount; // in USD
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private boolean active = true;
    private String paymentId;
}