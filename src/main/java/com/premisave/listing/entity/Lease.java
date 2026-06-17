package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "leases")
public class Lease extends Listing {

    private int leaseDurationMonths;
    private BigDecimal depositAmount;
    private String leaseTerms;
    private boolean renewable = false;
}