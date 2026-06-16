package com.premisave.listing.entity;

import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listings")
public class Listing extends BaseEntity {

    private String ownerId;
    private String title;
    private String description;
    private ListingCategory category;
    private ListingStatus status = ListingStatus.PENDING;

    private BigDecimal price;           // USD base
    private String currency = "USD";

    private Double latitude;
    private Double longitude;

    private String address;
    private String city;
    private String country;

    private String mainImageUrl;
    private java.util.List<String> imageUrls = new java.util.ArrayList<>();
}