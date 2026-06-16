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
    private BigDecimal amount;
    private String currency = "USD";
    private PaymentMethod method; // PAYPAL, STRIPE, MPESA, AIRTEL
    private PaymentStatus status = PaymentStatus.PENDING;
    private String transactionRef;
    private LocalDateTime paidAt;
}