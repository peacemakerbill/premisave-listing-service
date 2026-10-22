package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Data part of the answer to GET /internal/escrow/{holdReference}: the hold and all its payouts. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class EscrowDetails {
    private String reference;
    /** PENDING, HELD, PARTIALLY_PAID, PAID_OUT or FAILED. */
    private String status;
    private String senderEmail;
    private String beneficiaryEmail;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal totalDebited;
    private BigDecimal remainingAmount;
    private String currency;
    private String failureReason;
    private List<EscrowPayoutRecord> payouts = new ArrayList<>();
}
