package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "short_term_rentals")
public class ShortTermRental extends Listing {

    /**
     * Both optional, but at least one is required (enforced in
     * ListingService, not here) — a listing can offer just a day rate,
     * just a night rate, or both, letting the customer choose at booking
     * time. The inherited Listing.price/priceUnit fields are NOT a third
     * independent price: for this category they're auto-derived from
     * whichever of these two is lower (see
     * ListingService.applyPrimaryPriceFromShortTermRates), purely so
     * search/sort/price-range filtering — which only knows about the one
     * Listing.price field — still has something sensible to compare
     * against. The real, selectable prices are always these two fields.
     */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal pricePerDay;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal pricePerNight;

    private int maxGuests;
    private int bedrooms;
    private int bathrooms;
    private boolean hasWifi = false;
    private boolean hasKitchen = false;
    private List<String> amenities = new ArrayList<>();
}