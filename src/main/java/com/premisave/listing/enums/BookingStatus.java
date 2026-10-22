package com.premisave.listing.enums;

public enum BookingStatus {
    PENDING,       // created, payment not yet confirmed
    CONFIRMED,     // payment succeeded, upcoming or in-progress stay
    COMPLETED,     // checkOut has passed on a CONFIRMED booking, the stay happened
    CANCELLED,     // cancelled, refund handled per policy
    FAILED,        // payment declined
    NEEDS_REVIEW   // payment outcome could not be confirmed, a person has to check wallet-service
}
