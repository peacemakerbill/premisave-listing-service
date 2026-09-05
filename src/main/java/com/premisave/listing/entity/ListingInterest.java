package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A lead: a customer expressing interest in a listing (long-term rental,
 * lease, house sale, land sale) and providing contact details so the owner
 * can reach out directly. No money or booking flow attached — this is
 * purely a "contact me about this" record.
 *
 * Cancelling interest deletes the record entirely (no soft-delete/status
 * field) — the customer's contact info simply shouldn't be retained once
 * they've said they're no longer interested. That means no historical
 * audit trail of past interest exists once cancelled; worth knowing if
 * that's ever needed later.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listing_interests")
@CompoundIndex(name = "listing_customer_idx", def = "{'listingId': 1, 'customerId': 1}", unique = true)
public class ListingInterest extends BaseEntity {

    @Indexed
    private String listingId;

    /** Stored at creation time so cancellation emails don't need to
     *  re-fetch the listing (which could fail if it's been deleted since). */
    private String listingTitle;

    @Indexed
    private String listingOwnerId;

    @Indexed
    private String customerId;

    // Contact details as explicitly provided at the time of the inquiry —
    // not just pulled from the account profile, since the customer may
    // want to give different/dedicated contact info for this inquiry.
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private String message;
}