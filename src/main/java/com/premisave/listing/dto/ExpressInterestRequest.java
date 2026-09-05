package com.premisave.listing.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * No fullName/email/phoneNumber/address here anymore — that's the
 * customer's account data, fetched live from auth-service using the
 * JWT-authenticated userId rather than submitted (and stored) as free-form
 * input. "message" stays since it's inquiry content, not identity data.
 */
@Data
public class ExpressInterestRequest {

    @NotBlank(message = "Listing ID is required")
    private String listingId;

    /** Optional note to the owner, e.g. "Looking to move in next month". */
    private String message;
}