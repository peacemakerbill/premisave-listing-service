package com.premisave.listing.dto;

import com.premisave.listing.enums.PaymentMethod;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private String subscriptionId;
    private BigDecimal amount;
    private PaymentMethod method;
}