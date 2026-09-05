package com.premisave.listing.dto;

import com.premisave.listing.enums.PriceUnit;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BookingRequest {

    @NotBlank(message = "Listing ID is required")
    private String listingId;

    @NotNull(message = "Check-in date is required")
    @Future(message = "Check-in must be in the future")
    private LocalDateTime checkIn;

    @NotNull(message = "Check-out date is required")
    @Future(message = "Check-out must be in the future")
    private LocalDateTime checkOut;

    /** Must be PER_DAY or PER_NIGHT, and the listing must actually have
     *  that rate set (pricePerDay / pricePerNight respectively). */
    @NotNull(message = "priceUnit is required (PER_DAY or PER_NIGHT)")
    private PriceUnit priceUnit;
}