package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "short_term_rentals")
public class ShortTermRental extends Listing {

    private int maxGuests;
    private int bedrooms;
    private int bathrooms;
    private boolean hasWifi = false;
    private boolean hasKitchen = false;
    private List<String> amenities = new ArrayList<>();
}