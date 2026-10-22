package com.premisave.listing.dto.wallet_service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** Data part of wallet-service's GET /internal/transfer/{reference}. A record exists only after the money moved. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class WalletTransferRecord {
    private String id;
    private String senderEmail;
    private String recipientEmail;
    private BigDecimal amount;
    /** What the sender was charged: amount plus commission. */
    private BigDecimal totalDebited;
    private String currency;
    private String status;
    private String reference;
    private String failureReason;
}
