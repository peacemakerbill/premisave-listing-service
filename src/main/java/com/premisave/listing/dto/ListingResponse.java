package com.premisave.listing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListingResponse {
    private String message;
    private String listingId;
    private String title;
    private boolean success = true;

    public ListingResponse(String message, String listingId) {
        this.message = message;
        this.listingId = listingId;
    }
}