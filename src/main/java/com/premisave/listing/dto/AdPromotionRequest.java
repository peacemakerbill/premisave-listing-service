package com.premisave.listing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdPromotionRequest {

    @NotBlank(message = "Listing ID is required")
    private String listingId;

    @NotNull(message = "Number of days is required")
    @Min(value = 1, message = "Minimum 1 day required")
    private Integer days;

    private BigDecimal customDailyRate; // Optional: override default rate
}