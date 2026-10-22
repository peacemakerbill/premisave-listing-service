package com.premisave.listing.service;

import com.premisave.listing.entity.Booking;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.EscrowStatus;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * What an admin or finance user can do about bookings the automatic jobs
 * gave up on: an unconfirmed payment (status NEEDS_REVIEW) or an escrow
 * payout that kept failing (escrowStatus NEEDS_REVIEW).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingAdminService {

    private final BookingRepository bookingRepository;
    private final MongoTemplate mongoTemplate;
    private final BookingSlotService slotService;
    private final EscrowService escrowService;
    private final ListingService listingService;
    private final EmailService emailService;

    public Page<Booking> needsReview(Pageable pageable) {
        return bookingRepository.findByStatusOrEscrowStatus(
                BookingStatus.NEEDS_REVIEW, EscrowStatus.NEEDS_REVIEW, pageable);
    }

    /** Puts a parked payout back in the queue (after the cause, such as a frozen recipient wallet, is fixed). */
    public Booking retryPayout(String bookingId, String adminId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        EscrowStatus backTo = booking.getStatus() == BookingStatus.CANCELLED ? EscrowStatus.SETTLING : EscrowStatus.HELD;

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(bookingId),
                Criteria.where("escrowStatus").is(EscrowStatus.NEEDS_REVIEW)));
        Update update = new Update()
                .set("escrowStatus", backTo)
                .set("settlementAttempts", 0)
                .unset("escrowLeaseUntil")
                .unset("lastSettlementError")
                .set("updatedAt", LocalDateTime.now());
        Booking updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (updated == null) {
            throw new IllegalArgumentException("This booking's payout is not waiting for review.");
        }
        log.warn("Admin {} re-queued the payout for booking {} as {}", adminId, bookingId, backTo);
        return updated;
    }

    /**
     * For a booking whose payment could not be confirmed: after checking the
     * wallet history for the booking's reference, say whether the tenant was
     * actually charged.
     */
    public Booking resolvePayment(String bookingId, boolean paid, String adminId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        if (booking.getStatus() != BookingStatus.NEEDS_REVIEW) {
            throw new IllegalArgumentException("This booking's payment is not waiting for review.");
        }
        boolean toEscrow = Boolean.TRUE.equals(booking.getEscrowPayment());
        String note = "Resolved by admin " + adminId + ": " + (paid ? "tenant was charged" : "tenant was not charged");

        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(bookingId),
                Criteria.where("status").is(BookingStatus.NEEDS_REVIEW)));
        Update update = new Update().set("lastReconcileError", note).set("updatedAt", LocalDateTime.now());
        if (paid) {
            update.set("status", BookingStatus.CONFIRMED)
                    .set("paymentReference", "booking-" + bookingId)
                    .set("escrowStatus", toEscrow ? EscrowStatus.HELD : EscrowStatus.NONE);
        } else {
            update.set("status", BookingStatus.FAILED);
        }
        Booking updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (updated == null) {
            throw new IllegalArgumentException("This booking's payment is not waiting for review.");
        }

        if (paid) {
            emailService.sendBookingConfirmedEmails(
                    updated.getTenantEmail(), null, updated.getOwnerEmail(), null,
                    listingTitle(updated.getListingId()), updated, toEscrow, escrowService.releaseDelayHours());
        } else {
            slotService.release(bookingId);
        }
        log.warn("{} (booking {})", note, bookingId);
        return updated;
    }

    private String listingTitle(String listingId) {
        try {
            Object raw = listingService.getListingById(listingId);
            return (raw instanceof Listing l) ? l.getTitle() : "your listing";
        } catch (Exception e) {
            return "the listing";
        }
    }
}
