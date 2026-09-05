package com.premisave.listing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ExpressInterestRequest {

    @NotBlank(message = "Listing ID is required")
    private String listingId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phoneNumber;

    private String address;

    /** Optional note to the owner, e.g. "Looking to move in next month". */
    private String message;
}