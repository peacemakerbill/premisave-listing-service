package com.premisave.listing.entity;

import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "payments")
public class Payment extends BaseEntity {

    private String userId;
    private String subscriptionId;
    private String listingId;

    // ====================== CANONICAL AMOUNT (always KES) ======================

    /** The amount in KES — the system's canonical currency. Always stored regardless of what the user paid in. */
    private BigDecimal amountKes;

    // ====================== CHARGED AMOUNT (user's chosen currency) ======================

    /** The amount actually charged in the user's selected currency. */
    private BigDecimal amount;

    /** ISO 4217 currency code of the charged amount e.g. "KES", "USD", "EUR". */
    private String currency = "KES";

    /** The exchange rate used at charge time: 1 KES = ? currency. Stored for audit/reconciliation. */
    private BigDecimal exchangeRate;

    // ====================== PAYMENT DETAILS ======================

    private PaymentMethod method;
    private PaymentStatus status = PaymentStatus.PENDING;
    private String transactionRef;
    private LocalDateTime paidAt;
}