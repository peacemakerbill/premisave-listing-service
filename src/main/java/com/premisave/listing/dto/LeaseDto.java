package com.premisave.listing.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class LeaseDto {
    private String id;
    private String title;
    private int leaseDurationMonths;
    private BigDecimal depositAmount;
    private boolean renewable;
}