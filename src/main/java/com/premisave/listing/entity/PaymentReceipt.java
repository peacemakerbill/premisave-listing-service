package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "payment_receipts")
public class PaymentReceipt extends BaseEntity {

    private String paymentId;
    private String userId;
    private String receiptUrl;
    private String receiptNumber;
}