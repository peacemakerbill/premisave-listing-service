package com.premisave.listing.dto;

import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.PriceUnit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    private String id;
    private String listingId;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;
    private PriceUnit priceUnit;
    private BigDecimal totalAmount;
    private String currency;
    private BookingStatus status;
    private String message;
    private boolean success;
}