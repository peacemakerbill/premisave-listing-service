package com.premisave.listing.dto;

import com.premisave.listing.enums.ListingCategory;
import lombok.Data;

@Data
public class ListingCategoryRequest {

    private ListingCategory category;

    private String city;

    private Double minPrice;
    private Double maxPrice;

    private String query;
}