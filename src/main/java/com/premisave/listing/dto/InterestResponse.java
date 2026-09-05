package com.premisave.listing.dto;

import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Exactly one of "customer" or "owner" is populated, never both — whoever
 * is viewing the response already knows their own details, so only the
 * OTHER party is worth returning:
 *   - getMyInterests (viewed as the customer): owner is populated,
 *     customer is null
 *   - getInterestsForMyListings (viewed as the owner): customer is
 *     populated, owner is null
 * Both are fetched live from auth-service — never a stale stored
 * snapshot.
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
    private UserSummaryResponse owner;
}