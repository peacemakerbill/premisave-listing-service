package com.premisave.listing.entity;

import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listing_promotions")
public class ListingPromotion extends BaseEntity {

    private String listingId;
    private String ownerId;
    private int days;
    private BigDecimal dailyRate;
    private BigDecimal totalAmount;
    private String currency = "USD";
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String paymentId;
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
}