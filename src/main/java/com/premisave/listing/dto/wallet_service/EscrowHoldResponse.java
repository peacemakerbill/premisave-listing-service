package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** Data part of the answer to POST /internal/escrow/hold. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EscrowHoldResponse {
    private String reference;
    private String status;
    private String senderEmail;
    private String beneficiaryEmail;
    private BigDecimal amount;
    /** Wallet-service's commission, charged once on the hold and never refunded. */
    private BigDecimal commission;
    /** What the tenant was debited: amount plus commission. */
    private BigDecimal totalDebited;
    private BigDecimal remainingAmount;
    private String currency;
}
