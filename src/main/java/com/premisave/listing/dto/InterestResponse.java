package com.premisave.listing.dto;

import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * "customer" is fetched live from auth-service at response-building time
 * (full name, email, phone, address, profile picture) — never the stale
 * stored snapshot ListingInterest itself no longer keeps.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestResponse {
    private String id;
    private String listingId;
    private String listingTitle;
    private String message;
    private LocalDateTime createdAt;
    private UserSummaryResponse customer;
}