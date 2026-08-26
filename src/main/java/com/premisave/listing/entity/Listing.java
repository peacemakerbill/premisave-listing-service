package com.premisave.listing.entity;

import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listings")
public class Listing extends BaseEntity {

    @Indexed
    private String ownerId;
    private String title;
    private String description;
    private ListingCategory category;

    @Indexed
    private ListingStatus status = ListingStatus.PENDING;

    private BigDecimal price;

    /** Display/browse currency only — actual payment settlement is always
     *  KES via wallet-service, regardless of what's shown here. */
    private String currency = "USD";

    private Double latitude;
    private Double longitude;

    private String address;

    @Indexed
    private String city;

    private String country;

    private String mainImageUrl;
    private List<String> imageUrls = new ArrayList<>();

    // Promotion Fields
    private boolean isPromoted = false;
    private LocalDateTime promotionEndDate;
}