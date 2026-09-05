package com.premisave.listing.entity;

import com.premisave.listing.enums.BookingStatus;
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

@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "bookings")
@CompoundIndexes({
        // Backs the overlap-conflict check in BookingService — one listing,
        // date range, and status queried together on every booking attempt.
        @CompoundIndex(name = "listing_dates_status_idx", def = "{'listingId': 1, 'checkIn': 1, 'checkOut': 1, 'status': 1}"),
        // Backs the scheduled completion sweep (findByStatusAndCheckOutBefore)
        // — a cross-listing query, so it needs its own index rather than
        // reusing the one above, which leads with listingId.
        @CompoundIndex(name = "status_checkout_idx", def = "{'status': 1, 'checkOut': 1}")
})
public class Booking extends BaseEntity {

    @Indexed
    private String listingId;

    @Indexed
    private String tenantId;   // the customer who booked
    private String tenantEmail; // stable, stored as a convenience reference —
                                 // full name/phone/etc. are never stored,
                                 // always fetched live from auth-service

    @Indexed
    private String ownerId;    // the listing's owner, copied for query convenience
    private String ownerEmail; // same reasoning as tenantEmail

    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    /** Which of the listing's two rates (pricePerDay / pricePerNight) this
     *  booking was charged at. */
    private PriceUnit priceUnit;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal totalAmount;

    private String currency = "USD";

    @Indexed
    private BookingStatus status = BookingStatus.PENDING;

    /** The wallet-service transfer reference for the original payment
     *  (tenant -> owner). */
    private String paymentReference;

    /** The wallet-service transfer reference for the refund, if cancelled
     *  (owner -> tenant). Null until cancellation happens. */
    private String refundReference;

    private LocalDateTime cancelledAt;
    private String cancelledBy;       // userId of whoever cancelled (tenant or owner)
    private String cancellationReason;
}