package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "house_sales")
public class HouseSale extends Listing {

    private int bedrooms;
    private int bathrooms;
    private int floors;
    private double plotSize;
    private boolean hasGarage = false;
    private String propertyType;
}