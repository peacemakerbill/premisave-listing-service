package com.premisave.listing.entity;

import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.CancellationPolicy;
import com.premisave.listing.enums.EscrowStatus;
import com.premisave.listing.enums.PriceUnit;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "bookings")
@CompoundIndexes({
        @CompoundIndex(name = "listing_dates_status_idx", def = "{'listingId': 1, 'checkIn': 1, 'checkOut': 1, 'status': 1}"),
        @CompoundIndex(name = "status_checkout_idx", def = "{'status': 1, 'checkOut': 1}"),
        // Release job: held escrow whose check-in is old enough.
        @CompoundIndex(name = "escrow_checkin_idx", def = "{'escrowStatus': 1, 'checkIn': 1}"),
        // Reconciliation job: unconfirmed payments by age.
        @CompoundIndex(name = "status_created_idx", def = "{'status': 1, 'createdAt': 1}"),
        // One booking per (tenant, Idempotency-Key). Bookings without a key are left out of the index.
        @CompoundIndex(name = "tenant_idempotency_uidx", def = "{'tenantId': 1, 'idempotencyKey': 1}",
                unique = true, partialFilter = "{ 'idempotencyKey': { '$type': 'string' } }")
})
public class Booking extends BaseEntity {

    @Indexed
    private String listingId;

    @Indexed
    private String tenantId;
    private String tenantEmail;

    @Indexed
    private String ownerId;
    private String ownerEmail;

    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    private PriceUnit priceUnit;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;

    private String currency = "USD";

    @Indexed
    private BookingStatus status = BookingStatus.PENDING;

    /** Client-supplied Idempotency-Key from the create request, if any. */
    private String idempotencyKey;

    /** Price per unit and number of days/nights used to work out totalAmount. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal rate;
    private Integer units;

    /** True if the payment is held in a wallet-service escrow hold, false if it went straight to the owner. */
    private Boolean escrowPayment;

    /** Escrow only: what the tenant was debited (price plus wallet-service's commission) and the commission itself. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal fundingAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal commissionAmount;

    // ---------------- payment reconciliation ----------------

    /** While in the future, one instance is checking this unconfirmed payment. */
    private Date reconcileLeaseUntil;
    private int reconcileAttempts;
    private String lastReconcileError;

    /** Wallet-service reference of the tenant's payment. */
    private String paymentReference;

    /** Policy in force when the booking was made (the listing's own, or the default). */
    private CancellationPolicy cancellationPolicy;

    // ---------------- escrow ----------------

    private EscrowStatus escrowStatus = EscrowStatus.NONE;

    /** While set and in the future, one instance is working on this booking's payout. */
    private Date escrowLeaseUntil;

    private int settlementAttempts;
    private String lastSettlementError;

    /** Wallet-service reference of the payout to the owner after check-in. */
    private String releaseReference;
    private LocalDateTime releasedAt;

    // ---------------- cancellation ----------------

    private LocalDateTime cancelledAt;
    private String cancelledBy;
    private String cancellationReason;

    /** Percentage of totalAmount going back to the tenant. */
    private Integer refundPercent;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal refundAmount;

    /** Share of the total the owner keeps when a tenant cancels late. */
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal ownerPayoutAmount;

    private String refundReference;
    private String payoutReference;
    private boolean refundSettled;
    private boolean payoutSettled;
}
