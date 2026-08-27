package com.premisave.listing.entity;

import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listing_promotions")
@CompoundIndexes({
        // Matches the exact query pattern used by the hourly expiry
        // scheduler (findByEndDateBeforeAndPaymentStatus) — previously
        // unindexed, meaning that job scanned the whole collection.
        @CompoundIndex(name = "end_date_payment_status_idx", def = "{'endDate': 1, 'paymentStatus': 1}")
})
public class ListingPromotion extends BaseEntity {

    @Indexed
    private String listingId;

    @Indexed
    private String ownerId;

    private int days;
    // Stored as Decimal128, not Spring Data MongoDB's default String
    // representation for BigDecimal — see Listing.price for why that
    // matters for range queries.
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal dailyRate;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;

    // Wallet-service settles in KES; this was previously defaulted to USD,
    // inconsistent with everything else in the payment path.
    private String currency = "KES";

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Indexed
    private String paymentId;

    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
}