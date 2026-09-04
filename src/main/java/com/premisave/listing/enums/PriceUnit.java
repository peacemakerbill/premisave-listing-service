package com.premisave.listing.enums;

public enum PriceUnit {
    PER_NIGHT,  // overnight short-term rentals (the default for that category)
    PER_DAY,    // day-use listings, or hosts who prefer this framing over "night"
    PER_MONTH,  // long-term rentals, leases
    TOTAL       // one-time sale price (house sale, land sale)
}