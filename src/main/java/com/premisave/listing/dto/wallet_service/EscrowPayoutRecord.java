package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** One payout inside the answer to GET /internal/escrow/{holdReference}. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EscrowPayoutRecord {
    private String reference;
    private String recipientEmail;
    private BigDecimal amount;
    /** PENDING, SUCCESS or FAILED. */
    private String status;
    private String failureReason;
}
