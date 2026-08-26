package com.premisave.listing.dto.wallet_service;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WalletPaymentResponse {
    private boolean success;
    private String status;
    private String message;
    private String transactionId;
    private BigDecimal newBalance;
}