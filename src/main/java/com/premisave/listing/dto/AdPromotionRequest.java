package com.premisave.listing.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdPromotionRequest {

    @NotBlank(message = "Listing ID is required")
    private String listingId;

    @NotNull(message = "Number of days is required")
    @Min(value = 1, message = "Minimum 1 day required")
    private Integer days;

    /** One of the listing's own photos, shown on the promotion. The listing's cover photo is used when this is left out. */
    @Size(max = 500, message = "Image address is too long")
    private String imageUrl;

    /** A short line shown with the promotion. Optional. */
    @Size(max = 80, message = "The headline can have at most 80 characters")
    private String headline;
}