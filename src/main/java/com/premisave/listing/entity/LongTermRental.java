package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "long_term_rentals")
public class LongTermRental extends Listing {

    private int minLeaseMonths;
    private boolean furnished;
    private String tenantRequirements;
}