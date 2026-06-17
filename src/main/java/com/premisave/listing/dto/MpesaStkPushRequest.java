package com.premisave.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class MpesaStkPushRequest {

    @NotBlank(message = "Phone number is required")
    private String phoneNumber; // Format: 2547XXXXXXXX

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotBlank(message = "Account reference is required")
    private String accountReference; // e.g. "SUB-123" or "LISTING-456"

    private String transactionDesc = "Premisave Payment";
}