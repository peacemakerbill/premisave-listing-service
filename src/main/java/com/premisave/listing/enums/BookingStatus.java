package com.premisave.listing.enums;

public enum BookingStatus {
    PENDING,    // created, payment not yet confirmed
    CONFIRMED,  // payment succeeded, booking is active
    CANCELLED,  // cancelled, refund processed
    FAILED      // payment failed or wallet-service was unreachable
}