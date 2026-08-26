package com.premisave.listing.dto.wallet_service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletInternalPaymentRequest {
    private String userId;
    private BigDecimal amount;
    private String service;
    private String description;
    private String initiatedBy;
    private String reference;
}