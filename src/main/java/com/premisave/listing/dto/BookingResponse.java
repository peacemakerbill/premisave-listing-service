package com.premisave.listing.dto;

import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.PriceUnit;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * "tenant" and "owner" are fetched live from auth-service at
 * response-building time (full name, email, phone, address, profile
 * picture) — Booking itself only stores their stable userId/email.
 */
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
    private UserSummaryResponse tenant;
    private UserSummaryResponse owner;
}