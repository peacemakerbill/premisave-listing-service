package com.premisave.listing.enums;

public enum EscrowStatus {
    NONE,          // paid straight to the owner (escrow off, or a booking made before escrow existed)
    HELD,          // tenant's money sits in a wallet-service escrow hold
    RELEASED,      // paid out to the owner after check-in
    SETTLING,      // booking cancelled, refund and/or owner share still being paid out
    SETTLED,       // cancellation fully paid out
    NEEDS_REVIEW   // payout kept failing, a person has to look at it
}
