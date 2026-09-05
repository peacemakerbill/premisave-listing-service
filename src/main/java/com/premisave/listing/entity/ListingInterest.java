package com.premisave.listing.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A lead: a customer expressing interest in a listing (long-term rental,
 * lease, house sale, land sale). No money or booking flow attached — this
 * is purely a "contact me about this" record.
 *
 * Deliberately stores almost nothing about the customer beyond their
 * stable identifiers (customerId, customerEmail) — full name, phone
 * number, address, and profile picture are fetched live from auth-service
 * whenever a response is built (see ListingInterestService), never
 * persisted here, since those can change and a stored snapshot would
 * silently go stale. If auth-service is unreachable when that fetch
 * happens, the request fails with a clear error rather than silently
 * returning stale or missing data.
 *
 * Cancelling interest deletes the record entirely (no soft-delete/status
 * field) — nothing to retain once the customer says they're no longer
 * interested. That means no historical audit trail of past interest
 * exists once cancelled; worth knowing if that's ever needed later.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Document(collection = "listing_interests")
@CompoundIndex(name = "listing_customer_idx", def = "{'listingId': 1, 'customerId': 1}", unique = true)
public class ListingInterest extends BaseEntity {

    @Indexed
    private String listingId;

    /** Stored at creation time so cancellation notifications don't need to
     *  re-fetch the listing (which could fail if it's been deleted since). */
    private String listingTitle;

    @Indexed
    private String listingOwnerId;

    @Indexed
    private String customerId;

    /** Stable, unlike the rest of the customer's profile — stored as a
     *  convenience reference, not relied on as a fallback if auth-service
     *  is down (the live fetch is still required for anything else). */
    private String customerEmail;

    private String message;
}