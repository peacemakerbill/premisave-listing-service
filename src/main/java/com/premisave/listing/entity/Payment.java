package com.premisave.listing.entity;

import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "payments")
public class Payment extends BaseEntity {

    @Indexed
    private String userId;
    private String subscriptionId;
    private String listingId;

    // ====================== CANONICAL AMOUNT (always KES) ======================

    /** The amount in KES — the system's canonical currency, and what's
     *  actually sent to wallet-service. Always stored regardless of what
     *  currency a listing's price is displayed in. Stored as Decimal128,
     *  not Spring Data MongoDB's default String representation for
     *  BigDecimal — see Listing.price for why that matters for range
     *  queries. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amountKes;

    // ====================== CHARGED AMOUNT ======================

    /** Wallet-service settles everything in KES today, so amount/currency/
     *  exchangeRate below are effectively always KES/1.0 for wallet-routed
     *  payments. Kept as separate fields (rather than removed) in case a
     *  non-wallet, foreign-currency-charged path is reintroduced later. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal amount;
    private String currency = "KES";
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal exchangeRate;

    // ====================== PAYMENT DETAILS ======================

    // A `method` field (PAYPAL/STRIPE/MPESA/AIRTEL_MONEY) used to live here.
    // Removed rather than deprecated: payment provider is entirely
    // wallet-service's concern now, and MongoDB being schemaless means this
    // is safe to drop outright — old documents that still have a "method"
    // key just keep an unmapped field that Spring Data ignores on read.
    // PaymentMethod.java (the enum) can go too if nothing else references it
    // — worth double-checking dto/PaymentRequest.java, which wasn't touched
    // in this pass.

    private PaymentStatus status = PaymentStatus.PENDING;

    /**
     * The reference sent to wallet-service's /internal/payment call (its
     * "reference" field). Previously held the M-Pesa CheckoutRequestID;
     * repurposed as the general external-payment reference now that
     * wallet-service is the only payment executor. Indexed uniquely since
     * it's how a wallet-service response gets matched back to a local
     * Payment record.
     */
    @Indexed(unique = true)
    private String transactionRef;

    private LocalDateTime paidAt;
}