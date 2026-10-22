package com.premisave.listing.service;

import com.premisave.listing.dto.wallet_service.EscrowHoldRequest;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import com.premisave.listing.entity.Booking;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.EscrowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * Settles bookings whose payment outcome is unknown.
 *
 * When the call to wallet-service fails or times out, the money may or may
 * not have moved, so the booking is left PENDING (holding its nights). This
 * job finds PENDING bookings older than a few minutes and, for each one:
 *
 * 1. Looks the payment up by its reference (the escrow hold for escrow
 *    bookings, the transfer otherwise). A completed record means the money
 *    moved: the booking becomes CONFIRMED.
 * 2. With no record, sends the payment again with the same reference. If
 *    wallet-service answers that the reference is already used, the gateway
 *    checks the record again and treats it as paid.
 * 3. A clear refusal (insufficient funds, frozen wallet) means nothing moved:
 *    the booking becomes FAILED and its nights are freed. Errors that keep
 *    repeating end in NEEDS_REVIEW for an admin (see AdminBookingController).
 *
 * The job waits several minutes first so the original call has long finished.
 * Plain transfers have no unique index on the reference in wallet-service, so
 * two of them with the same reference at the same moment could both go
 * through. Escrow holds do have one, but a hold wallet-service is still
 * working on (PENDING) settles itself within a few minutes, so waiting also
 * avoids asking too early.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingReconciliationService {

    private static final String INITIATED_BY = "LISTING_SERVICE";

    private final MongoTemplate mongoTemplate;
    private final WalletTransferService walletTransfers;
    private final WalletEscrowService walletEscrow;
    private final BookingSlotService slotService;
    private final SchedulerLockService lockService;
    private final EscrowService escrowService;
    private final ListingService listingService;
    private final EmailService emailService;

    @Value("${booking.reconciliation.pending-after-minutes:3}")
    private int pendingAfterMinutes;

    @Value("${booking.reconciliation.max-attempts:5}")
    private int maxAttempts;

    @Value("${booking.reconciliation.lease-minutes:5}")
    private int leaseMinutes;

    @Value("${booking.reconciliation.retry-backoff-minutes:2}")
    private int retryBackoffMinutes;

    @Value("${booking.reconciliation.batch-size:50}")
    private int batchSize;

    public void reconcilePendingBookings() {
        lockService.runLocked("booking-reconciliation", Duration.ofMinutes(10), Duration.ofSeconds(30), this::doReconcile);
    }

    private void doReconcile() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(pendingAfterMinutes);
        Query due = new Query(new Criteria().andOperator(
                Criteria.where("status").is(BookingStatus.PENDING),
                Criteria.where("createdAt").lte(cutoff),
                leaseFree(new Date()))).limit(batchSize);

        List<Booking> candidates = mongoTemplate.find(due, Booking.class);
        for (Booking candidate : candidates) {
            Booking booking = claim(candidate.getId());
            if (booking != null) {
                reconcile(booking);
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Booking reconciliation looked at {} unconfirmed booking(s).", candidates.size());
        }
    }

    private void reconcile(Booking booking) {
        if (Boolean.TRUE.equals(booking.getEscrowPayment())) {
            reconcileEscrowHold(booking);
            return;
        }
        String reference = "booking-" + booking.getId();

        WalletTransferService.Result found = walletTransfers.lookup(reference);
        if (found.paid()) {
            confirm(booking, false, null, null);
            return;
        }
        if (found.status() == WalletTransferService.Status.DECLINED) {
            failPayment(booking, found.message());
            return;
        }

        String recipient = booking.getOwnerEmail();
        if (recipient == null || recipient.isBlank()) {
            markNeedsReview(booking.getId(), "no recipient account to retry the payment against");
            return;
        }
        BigDecimal amount = booking.getTotalAmount();

        WalletTransferService.Result retry = walletTransfers.transfer(new WalletTransferRequest(
                booking.getTenantId(), recipient, amount,
                "Booking payment (confirmation retry) for booking " + booking.getId(),
                reference, INITIATED_BY));

        switch (retry.status()) {
            case PAID -> confirm(booking, false, null, null);
            case DECLINED -> failPayment(booking, retry.message());
            default -> recordFailure(booking, retry.message());
        }
    }

    /** Same steps for a booking paid into a wallet-service escrow hold. */
    private void reconcileEscrowHold(Booking booking) {
        String reference = "booking-" + booking.getId();

        WalletEscrowService.HoldResult found = walletEscrow.lookupHold(reference);
        if (found.paid()) {
            confirm(booking, true, found.totalDebited(), found.commission());
            return;
        }
        if (found.status() == WalletTransferService.Status.DECLINED) {
            failPayment(booking, found.message());
            return;
        }

        String owner = booking.getOwnerEmail();
        if (owner == null || owner.isBlank()) {
            markNeedsReview(booking.getId(), "no owner account to retry the escrow hold against");
            return;
        }
        WalletEscrowService.HoldResult retry = walletEscrow.hold(new EscrowHoldRequest(
                booking.getTenantId(), owner, booking.getTotalAmount(),
                "Booking payment (confirmation retry) for booking " + booking.getId(),
                reference, INITIATED_BY));

        switch (retry.status()) {
            case PAID -> confirm(booking, true, retry.totalDebited(), retry.commission());
            case DECLINED -> failPayment(booking, retry.message());
            default -> recordFailure(booking, retry.message());
        }
    }

    /** A clear refusal: nothing moved, so the booking fails and its nights are freed. */
    private void failPayment(Booking booking, String reason) {
        Booking failed = mongoTemplate.findAndModify(
                new Query(new Criteria().andOperator(
                        Criteria.where("_id").is(booking.getId()),
                        Criteria.where("status").is(BookingStatus.PENDING))),
                new Update().set("status", BookingStatus.FAILED)
                        .set("lastReconcileError", reason)
                        .unset("reconcileLeaseUntil")
                        .set("updatedAt", LocalDateTime.now()),
                FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (failed != null) {
            slotService.release(failed.getId());
            log.warn("Booking failed by reconciliation, payment was refused: id={}, reason={}", failed.getId(), reason);
        }
    }

    private void confirm(Booking booking, boolean toEscrow, BigDecimal fundingAmount, BigDecimal commission) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(booking.getId()),
                Criteria.where("status").is(BookingStatus.PENDING)));
        Update update = new Update()
                .set("status", BookingStatus.CONFIRMED)
                .set("paymentReference", "booking-" + booking.getId())
                .set("escrowStatus", toEscrow ? EscrowStatus.HELD : EscrowStatus.NONE)
                .unset("reconcileLeaseUntil")
                .unset("lastReconcileError")
                .set("updatedAt", LocalDateTime.now());
        if (fundingAmount != null) {
            update.set("fundingAmount", fundingAmount);
        }
        if (commission != null) {
            update.set("commissionAmount", commission);
        }
        Booking confirmed = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
        if (confirmed == null) {
            return;
        }
        log.info("Booking confirmed by reconciliation: id={}, amount={}", confirmed.getId(), confirmed.getTotalAmount());
        emailService.sendBookingConfirmedEmails(
                confirmed.getTenantEmail(), null, confirmed.getOwnerEmail(), null,
                listingTitle(confirmed.getListingId()), confirmed, toEscrow, escrowService.releaseDelayHours());
    }

    private void markNeedsReview(String bookingId, String reason) {
        mongoTemplate.updateFirst(
                new Query(new Criteria().andOperator(
                        Criteria.where("_id").is(bookingId),
                        Criteria.where("status").is(BookingStatus.PENDING))),
                new Update().set("status", BookingStatus.NEEDS_REVIEW)
                        .set("lastReconcileError", reason)
                        .unset("reconcileLeaseUntil")
                        .set("updatedAt", LocalDateTime.now()),
                Booking.class);
        log.error("Booking payment needs review: id={}, reason={}", bookingId, reason);
    }

    private void recordFailure(Booking booking, String error) {
        int attempts = booking.getReconcileAttempts() + 1;
        if (attempts >= maxAttempts) {
            mongoTemplate.updateFirst(
                    new Query(Criteria.where("_id").is(booking.getId())),
                    new Update().set("reconcileAttempts", attempts)
                            .set("status", BookingStatus.NEEDS_REVIEW)
                            .set("lastReconcileError", error)
                            .unset("reconcileLeaseUntil")
                            .set("updatedAt", LocalDateTime.now()),
                    Booking.class);
            log.error("Booking payment needs review after {} attempts: id={}, lastError={}", attempts, booking.getId(), error);
            return;
        }
        long waitMillis = Duration.ofMinutes((long) retryBackoffMinutes * attempts).toMillis();
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(booking.getId())),
                new Update().set("reconcileAttempts", attempts)
                        .set("lastReconcileError", error)
                        .set("reconcileLeaseUntil", new Date(System.currentTimeMillis() + waitMillis))
                        .set("updatedAt", LocalDateTime.now()),
                Booking.class);
        log.warn("Booking reconciliation attempt {}/{} failed: id={}, error={}", attempts, maxAttempts, booking.getId(), error);
    }

    private Booking claim(String bookingId) {
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(bookingId),
                Criteria.where("status").is(BookingStatus.PENDING),
                leaseFree(new Date())));
        Update update = new Update().set("reconcileLeaseUntil",
                new Date(System.currentTimeMillis() + Duration.ofMinutes(leaseMinutes).toMillis()));
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
    }

    private String listingTitle(String listingId) {
        try {
            Object raw = listingService.getListingById(listingId);
            return (raw instanceof Listing l) ? l.getTitle() : "your listing";
        } catch (Exception e) {
            return "the listing";
        }
    }

    private static Criteria leaseFree(Date now) {
        return new Criteria().orOperator(
                Criteria.where("reconcileLeaseUntil").is(null),
                Criteria.where("reconcileLeaseUntil").lt(now));
    }
}
