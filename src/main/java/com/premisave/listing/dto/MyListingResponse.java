package com.premisave.listing.dto;

import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MyListingResponse {

    private String id;
    private String title;
    private String description;
    private ListingCategory category;
    private ListingStatus status;
    private BigDecimal price;
    private String currency;
    private String city;
    private String mainImageUrl;
    private List<String> imageUrls;

    // Promotion Details
    private boolean isPromoted;
    private LocalDateTime promotionEndDate;
    private Integer daysRemaining;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}