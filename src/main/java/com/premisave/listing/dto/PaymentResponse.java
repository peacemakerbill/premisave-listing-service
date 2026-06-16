package com.premisave.listing.dto;

import com.premisave.listing.enums.PaymentStatus;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentResponse {
    private String paymentId;
    private BigDecimal amount;
    private PaymentStatus status;
    private String message;
}