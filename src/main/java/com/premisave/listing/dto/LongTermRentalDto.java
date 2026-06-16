package com.premisave.listing.dto;

import lombok.Data;

@Data
public class LongTermRentalDto {
    private String id;
    private String title;
    private int minLeaseMonths;
    private boolean furnished;
}