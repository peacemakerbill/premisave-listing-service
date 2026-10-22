package com.premisave.listing.repository;

import com.premisave.listing.entity.Booking;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.EscrowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends MongoRepository<Booking, String> {

    Page<Booking> findByTenantId(String tenantId, Pageable pageable);

    Page<Booking> findByTenantIdAndStatus(String tenantId, BookingStatus status, Pageable pageable);

    Page<Booking> findByOwnerId(String ownerId, Pageable pageable);

    Page<Booking> findByOwnerIdAndStatus(String ownerId, BookingStatus status, Pageable pageable);

    Page<Booking> findByStatusOrEscrowStatus(BookingStatus status, EscrowStatus escrowStatus, Pageable pageable);

    Optional<Booking> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    /**
     * Friendly pre-check for overlapping stays. The real guarantee against
     * double booking is the per-night claim in BookingSlotService; this just
     * lets us answer most conflicts without touching it.
     */
    @Query("{ 'listingId': ?0, 'status': { $in: ['PENDING', 'CONFIRMED'] }, "
            + "'checkIn': { $lt: ?2 }, 'checkOut': { $gt: ?1 } }")
    List<Booking> findOverlapping(String listingId, LocalDateTime checkIn, LocalDateTime checkOut);

    List<Booking> findByStatusAndCheckOutBefore(BookingStatus status, LocalDateTime checkOut);
}
