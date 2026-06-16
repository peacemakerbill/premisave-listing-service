package com.premisave.listing.dto;

import lombok.Data;

import java.util.List;

@Data
public class ShortTermRentalDto {
    private String id;
    private String title;
    private int maxGuests;
    private int bedrooms;
    private int bathrooms;
    private boolean hasWifi;
    private boolean hasKitchen;
    private List<String> amenities;
}