package com.premisave.listing.entity;

import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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
     *  currency a listing's price is displayed in. */
    private BigDecimal amountKes;

    // ====================== CHARGED AMOUNT ======================

    /** Wallet-service settles everything in KES today, so amount/currency/
     *  exchangeRate below are effectively always KES/1.0 for wallet-routed
     *  payments. Kept as separate fields (rather than removed) in case a
     *  non-wallet, foreign-currency-charged path is reintroduced later. */
    private BigDecimal amount;
    private String currency = "KES";
    private BigDecimal exchangeRate;

    // ====================== PAYMENT DETAILS ======================

    /**
     * @deprecated Payment provider/method is now entirely wallet-service's
     * concern — it may fund a wallet via M-Pesa, PayPal, Flutterwave,
     * Stripe, or NOWPayments, but listing-service only ever asks it to debit
     * an existing balance and no longer knows or needs to know how that
     * wallet was funded. Left on the entity (nullable, unset by new code)
     * rather than removed, since existing stored documents may have a value
     * here.
     */
    @Deprecated
    private PaymentMethod method;

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