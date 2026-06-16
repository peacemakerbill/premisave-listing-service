package com.premisave.listing.enums;

public enum SubscriptionPlan {
    BASIC(1),      // 1 month
    PREMIUM(3),    // 3 months
    ULTIMATE(12);  // 12 months

    private final int months;

    SubscriptionPlan(int months) {
        this.months = months;
    }

    public int getMonths() {
        return months;
    }
}