package com.premisave.listing.entity;

import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.enums.PriceUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

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

    // Stored as Decimal128 (NumberDecimal), not the Spring Data MongoDB
    // default of String for BigDecimal — a string-typed price field can
    // never match a numeric $gte/$lte range query, which is exactly what
    // was silently breaking minPrice/maxPrice filtering. Existing
    // documents created before this annotation was added will still have
    // price stored as a string and need a one-time migration (see
    // CHANGES.md) — this only fixes the mapping going forward.
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;

    /** What the price above actually means — per-night, per-month, or a
     *  one-time total. No hardcoded default here: ListingService derives a
     *  sensible one per category if the request doesn't specify one, since
     *  "monthly" vs "total" was previously an unwritten, unenforced
     *  convention nothing in the API actually stated. */
    private PriceUnit priceUnit;

    /** Display/browse currency — currently always USD, matching what
     *  wallet-service actually settles in. */
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