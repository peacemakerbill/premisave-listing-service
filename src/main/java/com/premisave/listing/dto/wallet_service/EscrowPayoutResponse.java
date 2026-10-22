package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** Data part of the answer to POST /internal/escrow/{holdReference}/payouts. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EscrowPayoutResponse {
    private String holdReference;
    private String reference;
    private String status;
    private String recipientEmail;
    private BigDecimal amount;
    private BigDecimal holdRemainingAmount;
    private String holdStatus;
    private String currency;
}
