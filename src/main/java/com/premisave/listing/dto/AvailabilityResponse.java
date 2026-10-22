package com.premisave.listing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityResponse {
    private String listingId;
    private LocalDate from;
    private LocalDate to;
    /** Nights (yyyy-MM-dd) that are booked or blocked inside the range. */
    private List<String> unavailableDates;
}
