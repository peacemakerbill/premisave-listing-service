package com.premisave.listing.entity;

import com.premisave.listing.enums.SubscriptionPlan;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "subscriptions")
public class Subscription extends BaseEntity {

    private String ownerId;
    private SubscriptionPlan plan;
    private BigDecimal amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String paymentId;

    /**
     * Tracks whether this subscription is still active.
     * Separate from BaseEntity.active (which is for listing visibility).
     * Set to false when cancelled or when expiry scheduler runs.
     */
    @Field("subscription_active")
    private boolean subscriptionActive = true;
}