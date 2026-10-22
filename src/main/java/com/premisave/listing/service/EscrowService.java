package com.premisave.listing.service;

import com.premisave.listing.config.EscrowProperties;
import com.premisave.listing.dto.wallet_service.EscrowPayoutRequest;
import com.premisave.listing.entity.Booking;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.EscrowStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Everything that moves money out of a booking's escrow hold in wallet-service:
 * paying the owner after check-in, and paying out a cancellation (refund to
 * the tenant, any kept share to the owner).
 *
 * The hold was made when the tenant paid (see BookingService), with the
 * reference booking-ID. Each payout is a call on that hold with a reference
 * that is the same every time for a given booking (booking-release-ID,
 * booking-refund-ID, booking-payout-ID), so a retry reuses it. Wallet-service
 * refuses a repeated reference, so a payout can never be made twice, and it
 * charges no commission on payouts: the owner receives exactly the share
 * stored on the booking.
 *
 * A "lease" (escrowLeaseUntil) marks a booking as being worked on. Cancel,
 * release and retry all check it before touching the booking, so they never
 * pay the same booking twice at the same moment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

    private static final String INITIATED_BY = "LISTING_SERVICE";

    private final EscrowProperties props;
    private final WalletEscrowService walletEscrow;
    private final MongoTemplate mongoTemplate;
    private final SchedulerLockService lockService;

    private record Outcome(boolean ok, String error) {
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public int releaseDelayHours() {
        return props.getReleaseDelayHours();
    }

    // ====================== CANCEL ======================

    /**
     * Atomically turns a CONFIRMED, held booking into CANCELLED / SETTLING and
     * records how the money is split. Returns null if it could not be done:
     * someone else already cancelled it, it is not held, or a payout is in
     * progress right now.
     */
    public Booking markCancelled(String bookingId, String cancelledBy, String reason,
                                 int refundPercent, BigDecimal refund, BigDecimal ownerShare) {
        Date now = new Date();
        Query query = new Query(new Criteria().andOperator(
                Criteria.where("_id").is(bookingId),
                Criteria.where("status").is(BookingStatus.CONFIRMED),
                Criteria.where("escrowStatus").is(EscrowStatus.HELD),
                leaseFree(now)));

        Update update = new Update()
                .set("status", BookingStatus.CANCELLED)
                .set("escrowStatus", EscrowStatus.SETTLING)
                .set("cancelledAt", LocalDateTime.now())
                .set("cancelledBy", cancelledBy)
                .set("cancellationReason", reason)
                .set("refundPercent", refundPercent)
                .set("refundAmount", refund)
                .set("ownerPayoutAmount", ownerShare)
                .set("refundReference", "booking-refund-" + bookingId)
                .set("payoutReference", "booking-payout-" + bookingId)
                .set("refundSettled", refund.signum() == 0)
                .set("payoutSettled", ownerShare.signum() == 0)
                .set("settlementAttempts", 0)
                .set("escrowLeaseUntil", leaseEnd())
                .set("updatedAt", LocalDateTime.now());

        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
    }

    /**
     * Pays out whatever part of a cancellation is still outstanding. The
     * caller must hold the lease (markCancelled and the retry job both do).
     * Never throws: failures are recorded on the booking and retried later.
     */
    public Booking settleCancellation(Booking booking) {
        String id = booking.getId();
        boolean refundDone = booking.isRefundSettled();
        boolean payoutDone = booking.isPayoutSettled();
        String error = null;

        if (!refundDone) {
            Outcome outcome = payOut(booking, booking.getTenantEmail(), booking.getRefundAmount(),
                    "Refund for cancelled booking " + id, booking.getRefundReference());
            if (outcome.ok()) {
                refundDone = true;
                apply(id, new Update().set("refundSettled", true));
            } else {
                error = "refund: " + outcome.error();
            }
        }

        if (!payoutDone) {
            Outcome outcome = payOut(booking, booking.getOwnerEmail(), booking.getOwnerPayoutAmount(),
                    "Amount kept after cancellation of booking " + id, booking.getPayoutReference());
            if (outcome.ok()) {
                payoutDone = true;
                apply(id, new Update().set("payoutSettled", true));
            } else if (error == null) {
                error = "owner payout: " + outcome.error();
            }
        }

        if (refundDone && payoutDone) {
            apply(id, new Update().set("escrowStatus", EscrowStatus.SETTLED)
                    .unset("escrowLeaseUntil").unset("lastSettlementError"));
            log.info("Cancellation settled: booking={}", id);
        } else {
            recordFailure(booking, error);
        }
        return mongoTemplate.findById(id, Booking.class);
    }

    // ====================== SCHEDULED WORK ======================

    /** Pays owners for held bookings whose check-in was at least releaseDelayHours ago. */
    public void releaseDueEscrow() {
        if (!props.isEnabled()) {
            return;
        }
        lockService.runLocked("escrow-release", Duration.ofMinutes(10), Duration.ofSeconds(30), this::doRelease);
    }

    /** Retries cancellations whose refund or owner share has not gone through yet. */
    public void retryPendingSettlements() {
        if (!props.isEnabled()) {
            return;
        }
        lockService.runLocked("escrow-settlement-retry", Duration.ofMinutes(10), Duration.ofSeconds(30), this::doRetry);
    }

    private void doRelease() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(props.getReleaseDelayHours());
        Criteria activeStay = Criteria.where("status").in(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);

        Query due = new Query(new Criteria().andOperator(
                Criteria.where("escrowStatus").is(EscrowStatus.HELD),
                activeStay,
                Criteria.where("checkIn").lte(cutoff),
                leaseFree(new Date()))).limit(props.getBatchSize());

        List<Booking> candidates = mongoTemplate.find(due, Booking.class);
        int released = 0;
        for (Booking candidate : candidates) {
            Booking booking = claim(candidate.getId(), EscrowStatus.HELD, activeStay);
            if (booking != null && release(booking)) {
                released++;
            }
        }
        if (!candidates.isEmpty()) {
            log.info("Escrow release: {} of {} due booking(s) paid out.", released, candidates.size());
        }
    }

    private boolean release(Booking booking) {
        String reference = "booking-release-" + booking.getId();
        // The owner receives the whole price: payouts carry no commission.
        BigDecimal ownerAmount = booking.getTotalAmount();
        Outcome outcome = payOut(booking, booking.getOwnerEmail(), ownerAmount,
                "Payout for booking " + booking.getId(), reference);
        if (outcome.ok()) {
            apply(booking.getId(), new Update()
                    .set("ownerPayoutAmount", ownerAmount)
                    .set("escrowStatus", EscrowStatus.RELEASED)
                    .set("releasedAt", LocalDateTime.now())
                    .set("releaseReference", reference)
                    .unset("escrowLeaseUntil")
                    .unset("lastSettlementError"));
            log.info("Escrow released: booking={}, owner={}, amount={}",
                    booking.getId(), booking.getOwnerId(), ownerAmount);
            return true;
        }
        recordFailure(booking, "release: " + outcome.error());
        return false;
    }

    private void doRetry() {
        Query pending = new Query(new Criteria().andOperator(
                Criteria.where("escrowStatus").is(EscrowStatus.SETTLING),
                leaseFree(new Date()))).limit(props.getBatchSize());

        for (Booking candidate : mongoTemplate.find(pending, Booking.class)) {
            Booking booking = claim(candidate.getId(), EscrowStatus.SETTLING, null);
            if (booking != null) {
                settleCancellation(booking);
            }
        }
    }

    // ====================== HELPERS ======================

    /** Pays one share of the booking's escrow hold to the tenant or the owner. */
    private Outcome payOut(Booking booking, String recipientEmail, BigDecimal amount, String description,
                           String reference) {
        if (recipientEmail == null || recipientEmail.isBlank()) {
            return new Outcome(false, "recipient email is missing");
        }
        if (amount == null || amount.signum() <= 0) {
            return new Outcome(false, "nothing to pay out");
        }
        EscrowPayoutRequest request = new EscrowPayoutRequest(recipientEmail, amount, description, reference, INITIATED_BY);
        WalletTransferService.Result result = walletEscrow.payout(holdReference(booking), request);
        if (result.paid()) {
            return new Outcome(true, null);
        }
        // A repeated reference (409) has already been checked against wallet-service's own record by
        // WalletEscrowService, so a payout that went through earlier comes back as paid, not as a failure.
        return new Outcome(false, result.status() + ": " + result.message());
    }

    /** The reference the booking's escrow hold was made with. */
    static String holdReference(Booking booking) {
        return "booking-" + booking.getId();
    }

    /** Takes the lease on a booking if it is in the expected state and nobody else holds it. */
    private Booking claim(String bookingId, EscrowStatus expected, Criteria extra) {
        Criteria idAndState = new Criteria().andOperator(
                Criteria.where("_id").is(bookingId),
                Criteria.where("escrowStatus").is(expected),
                leaseFree(new Date()));
        Query query = extra == null ? new Query(idAndState) : new Query(new Criteria().andOperator(idAndState, extra));
        Update update = new Update().set("escrowLeaseUntil", leaseEnd());
        return mongoTemplate.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Booking.class);
    }

    private void recordFailure(Booking booking, String error) {
        int attempts = booking.getSettlementAttempts() + 1;
        String message = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        Update update = new Update().set("settlementAttempts", attempts).set("lastSettlementError", message);

        if (attempts >= props.getMaxAttempts()) {
            update.set("escrowStatus", EscrowStatus.NEEDS_REVIEW).unset("escrowLeaseUntil");
            log.error("Escrow payout needs review after {} attempts: booking={}, lastError={}",
                    attempts, booking.getId(), message);
        } else {
            long waitMillis = Duration.ofMinutes((long) props.getRetryBackoffMinutes() * attempts).toMillis();
            update.set("escrowLeaseUntil", new Date(System.currentTimeMillis() + waitMillis));
            log.warn("Escrow payout failed (attempt {}/{}), will retry: booking={}, error={}",
                    attempts, props.getMaxAttempts(), booking.getId(), message);
        }
        apply(booking.getId(), update);
    }

    private void apply(String bookingId, Update update) {
        update.set("updatedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(bookingId)), update, Booking.class);
    }

    private Date leaseEnd() {
        return new Date(System.currentTimeMillis() + Duration.ofMinutes(props.getLeaseMinutes()).toMillis());
    }

    private static Criteria leaseFree(Date now) {
        return new Criteria().orOperator(
                Criteria.where("escrowLeaseUntil").is(null),
                Criteria.where("escrowLeaseUntil").lt(now));
    }
}
