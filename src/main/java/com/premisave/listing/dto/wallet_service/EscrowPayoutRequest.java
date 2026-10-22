package com.premisave.listing.dto.wallet_service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body of wallet-service's POST /internal/escrow/{holdReference}/payouts. No
 * commission is charged. The recipient must be the tenant (a refund) or the
 * owner named when the hold was made.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EscrowPayoutRequest {
    private String recipientAccountNumber;
    private BigDecimal amount;
    private String description;
    /** Idempotency key for this payout, for example booking-release-ID. */
    private String reference;
    private String initiatedBy;
}
