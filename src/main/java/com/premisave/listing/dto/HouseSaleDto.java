package com.premisave.listing.dto;

import lombok.Data;

@Data
public class HouseSaleDto {
    private String id;
    private String title;
    private int bedrooms;
    private int bathrooms;
    private int floors;
    private double plotSize;
    private boolean hasGarage;
    private String propertyType;
}