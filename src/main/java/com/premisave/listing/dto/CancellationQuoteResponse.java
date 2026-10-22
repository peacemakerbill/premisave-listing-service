package com.premisave.listing.dto;

import com.premisave.listing.enums.CancellationPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CancellationQuoteResponse {
    private String bookingId;
    private CancellationPolicy policy;
    private boolean cancelledByOwner;
    private long hoursUntilCheckIn;
    private int refundPercent;
    private BigDecimal refundAmount;
    private BigDecimal ownerAmount;
    private String currency;
    private String message;
}
