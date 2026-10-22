package com.premisave.listing.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * One row per listing per night. The id is "listingId|yyyy-MM-dd", so MongoDB's
 * own unique _id index is what stops two bookings from taking the same night.
 * Rows are deleted when a booking fails or is cancelled, and expire on their
 * own a week after check-out.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "booking_slots")
public class BookingSlot {

    @Id
    private String id;

    @Indexed
    private String bookingId;

    private String listingId;
    private String day;

    @Indexed(expireAfter = "0s")
    private Date expireAt;
}
