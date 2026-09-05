package com.premisave.listing.enums;

public enum BookingStatus {
    PENDING,    // created, payment not yet confirmed
    CONFIRMED,  // payment succeeded, upcoming or in-progress stay
    COMPLETED,  // checkOut has passed on a CONFIRMED booking — the stay happened
    CANCELLED,  // cancelled, refund processed
    FAILED      // payment failed or wallet-service was unreachable
}