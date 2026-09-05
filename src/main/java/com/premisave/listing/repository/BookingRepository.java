package com.premisave.listing.repository;

import com.premisave.listing.entity.Booking;
import com.premisave.listing.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface BookingRepository extends MongoRepository<Booking, String> {

    Page<Booking> findByTenantId(String tenantId, Pageable pageable);

    Page<Booking> findByTenantIdAndStatus(String tenantId, BookingStatus status, Pageable pageable);

    Page<Booking> findByOwnerId(String ownerId, Pageable pageable);

    Page<Booking> findByOwnerIdAndStatus(String ownerId, BookingStatus status, Pageable pageable);

    /**
     * Overlap check: a conflicting booking exists if its date range
     * intersects the requested one at all — the standard "A starts before
     * B ends AND B starts before A ends" interval-overlap condition.
     * Restricted to PENDING/CONFIRMED, since CANCELLED/FAILED bookings
     * don't actually hold the dates.
     */
    @Query("{ 'listingId': ?0, 'status': { $in: ['PENDING', 'CONFIRMED'] }, "
            + "'checkIn': { $lt: ?2 }, 'checkOut': { $gt: ?1 } }")
    List<Booking> findOverlapping(String listingId, LocalDateTime checkIn, LocalDateTime checkOut);
}