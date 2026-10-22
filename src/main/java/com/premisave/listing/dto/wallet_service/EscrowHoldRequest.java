package com.premisave.listing.dto.wallet_service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body of wallet-service's POST /internal/escrow/hold. The amount is the booking
 * price; wallet-service adds its commission on top and debits the tenant once.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EscrowHoldRequest {
    private String senderUserId;
    /** The owner's wallet account number (email). Checked now so the owner can be paid later. */
    private String beneficiaryEmail;
    private BigDecimal amount;
    private String description;
    /** Idempotency key, for example booking-ID. */
    private String reference;
    private String initiatedBy;
}
