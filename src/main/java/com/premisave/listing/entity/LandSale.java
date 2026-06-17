package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "land_sales")
public class LandSale extends Listing {

    private double sizeInAcres;
    private String landUseType;
    private boolean hasTitleDeed = false;
}