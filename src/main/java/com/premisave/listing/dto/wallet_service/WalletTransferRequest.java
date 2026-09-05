package com.premisave.listing.dto.wallet_service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for wallet-service's POST /internal/transfer endpoint —
 * moves money between two wallet accounts (as opposed to /internal/payment,
 * which debits one account for a service charge).
 *
 * ASSUMPTION, flagged clearly: recipientAccountNumber's confirmed example
 * value ("recipient@example.com") strongly suggests this is the
 * recipient's email address, not a separate wallet account number this
 * service has any way to look up. Resolved via
 * AuthServiceClient.getUserSummary(...).getEmail() in BookingService. If
 * wallet-service actually expects a different identifier, only the
 * resolution call site needs to change, not this DTO's shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransferRequest {
    private String senderUserId;
    private String recipientAccountNumber;
    private BigDecimal amount;
    private String description;
    private String reference;
    private String initiatedBy;
}