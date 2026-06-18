package com.premisave.listing.dto;

import com.premisave.listing.enums.ListingCategory;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ListingUpdateRequest {

    // All fields optional — no @NotNull, no @NotBlank
    private String title;
    private String description;
    private ListingCategory category;

    @Positive(message = "Price must be positive")
    private BigDecimal price;

    private Double latitude;
    private Double longitude;
    private String address;
    private String city;
    private String country;

    private String mainImageUrl;
    private List<String> imageUrls;

    // Short Term Rental
    private Integer maxGuests;
    private Integer bedrooms;
    private Integer bathrooms;
    private Boolean hasWifi;
    private Boolean hasKitchen;
    private List<String> amenities;

    // Long Term Rental
    private Integer minLeaseMonths;
    private Boolean furnished;
    private String tenantRequirements;

    // Land Sale
    private Double sizeInAcres;
    private String landUseType;
    private Boolean hasTitleDeed;

    // House Sale
    private Integer floors;
    private Double plotSize;
    private Boolean hasGarage;
    private String propertyType;

    // Lease
    private Integer leaseDurationMonths;
    private BigDecimal depositAmount;
    private String leaseTerms;
    private Boolean renewable;
}