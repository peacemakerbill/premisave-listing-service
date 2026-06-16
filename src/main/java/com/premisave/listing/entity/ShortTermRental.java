package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "short_term_rentals")
public class ShortTermRental extends Listing {

    private int maxGuests;
    private int bedrooms;
    private int bathrooms;
    private boolean hasWifi;
    private boolean hasKitchen;
    private java.util.List<String> amenities = new java.util.ArrayList<>();
}