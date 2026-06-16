package com.premisave.listing.dto;

import lombok.Data;

@Data
public class LandSaleDto {
    private String id;
    private String title;
    private double sizeInAcres;
    private String landUseType;
    private boolean hasTitleDeed;
}