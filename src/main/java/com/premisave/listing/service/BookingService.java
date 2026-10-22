package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.BookingRequest;
import com.premisave.listing.dto.BookingResponse;
import com.premisave.listing.dto.CancellationQuoteResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.dto.wallet_service.EscrowHoldRequest;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import com.premisave.listing.entity.Booking;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.EscrowStatus;
import com.premisave.listing.enums.PriceUnit;
import com.premisave.listing.exception.AuthenticationFailedException;
import com.premisave.listing.exception.BookingConflictException;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import com.premisave.listing.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Short-term rental bookings.
 *
 * Paying: with escrow on, wallet-service holds the money (one escrow hold, the
 * wallet fee charged once on top of the price) and pays the owner after
 * check-in; with escrow off, the tenant pays the owner directly. Before any money moves, the
 * booking claims one slot per night (BookingSlotService), which is what makes
 * double booking impossible. A client may send an Idempotency-Key so a retry
 * returns the original booking instead of creating a second one.
 *
 * Cancelling: the refund follows the listing's cancellation policy (owners
 * cancelling always refund in full). With escrow, the booking is marked
 * cancelled first, then the payouts are made from the escrow hold, with no
 * further fee; anything that fails is retried by EscrowService. The fee paid
 * when booking is never refunded.
 *
 * None of this uses database transactions: each step is a single atomic
 * write, and remote wallet calls do not belong inside a transaction anyway.
 *
 * ASSUMPTION carried from WalletTransferRequest: recipientAccountNumber is
 * the recipient's email.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String INITIATED_BY = "LISTING_SERVICE";

    private final BookingRepository bookingRepository;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;
    private final WalletTransferService walletTransfers;
    private final WalletEscrowService walletEscrow;
    private final CommissionService commissionService;
    private final EmailService emailService;
    private final BookingSlotService slotService;
    private final EscrowService escrowService;
    private final CancellationPolicyService policyService;
    private final SchedulerLockService lockService;

    @Value("${booking.max-stay-days:90}")
    private int maxStayDays;

    // ====================== CREATE (PAY) ======================

    public BookingResponse createBooking(BookingRequest request, String tenantId, String authHeader,
                                         String idempotencyKey) {
        if (!request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new IllegalArgumentException("checkOut must be after checkIn.");
        }
        String key = normaliseKey(idempotencyKey);

        UserSummaryResponse tenant = authServiceClient.getCurrentUser(authHeader);
        if (tenant == null || !tenant.getId().equals(tenantId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        if (key != null) {
            Optional<Booking> existing = bookingRepository.findByTenantIdAndIdempotencyKey(tenantId, key);
            if (existing.isPresent()) {
                return replay(existing.get(), request, tenant, authHeader);
            }
        }

        Object rawListing = listingService.getListingById(request.getListingId());
        if (!(rawListing instanceof ShortTermRental listing)) {
            throw new IllegalArgumentException("Bookings are only supported for short-term rental listings.");
        }

        // Nights are counted by calendar date (check-in date to check-out date), the same
        // dates BookingSlotService reserves, so what is charged matches what is blocked.
        if (listing.getOwnerId().equals(tenantId)) {
            throw new IllegalArgumentException("You cannot book your own listing.");
        }

        BigDecimal rate = resolveRate(listing, request.getPriceUnit());
        long units = ChronoUnit.DAYS.between(request.getCheckIn().toLocalDate(), request.getCheckOut().toLocalDate());
        if (units < 1) {
            throw new IllegalArgumentException("Booking must span at least one day/night.");
        }
        if (units > maxStayDays) {
            throw new IllegalArgumentException("A booking can span at most " + maxStayDays + " days/nights.");
        }
        BigDecimal totalAmount = rate.multiply(BigDecimal.valueOf(units));

        boolean escrow = escrowService.isEnabled();

        if (!bookingRepository.findOverlapping(listing.getId(), request.getCheckIn(), request.getCheckOut()).isEmpty()) {
            throw new BookingConflictException(
                    "This listing is already booked for part or all of the requested dates.");
        }

        UserSummaryResponse owner = authServiceClient.getUserSummary(listing.getOwnerId(), authHeader);
        if (owner == null || owner.getEmail() == null) {
            throw new IllegalArgumentException("Could not resolve the listing owner's account for payment.");
        }

        Booking booking = new Booking();
        booking.setId(new ObjectId().toHexString());
        booking.setCreatedAt(LocalDateTime.now());
        booking.setListingId(listing.getId());
        booking.setTenantId(tenantId);
        booking.setTenantEmail(tenant.getEmail());
        booking.setOwnerId(listing.getOwnerId());
        booking.setOwnerEmail(owner.getEmail());
        booking.setCheckIn(request.getCheckIn());
        booking.setCheckOut(request.getCheckOut());
        booking.setPriceUnit(request.getPriceUnit());
        booking.setTotalAmount(totalAmount);
        booking.setRate(rate);
        booking.setUnits((int) units);
        booking.setEscrowPayment(escrow);
        booking.setCancellationPolicy(policyService.resolve(listing.getCancellationPolicy()));
        booking.setIdempotencyKey(key);
        booking.setStatus(BookingStatus.PENDING);

        // Claim the nights first. If another request got there first, this throws a 409
        // (or, for a retried request with the same key, returns the earlier booking).
        try {
            slotService.claim(listing.getId(), booking.getId(), request.getCheckIn(), request.getCheckOut());
        } catch (BookingConflictException e) {
            Optional<Booking> raced = key == null
                    ? Optional.empty()
                    : bookingRepository.findByTenantIdAndIdempotencyKey(tenantId, key);
            if (raced.isPresent()) {
                return replay(raced.get(), request, tenant, authHeader);
            }
            throw e;
        }

        try {
            booking = bookingRepository.save(booking);
        } catch (DuplicateKeyException e) {
            slotService.release(booking.getId());
            Optional<Booking> raced = key == null
                    ? Optional.empty()
                    : bookingRepository.findByTenantIdAndIdempotencyKey(tenantId, key);
            if (raced.isPresent()) {
                return replay(raced.get(), request, tenant, authHeader);
            }
            throw e;
        }

        String reference = "booking-" + booking.getId();
        WalletTransferService.Status outcome;
        String outcomeMessage;
        BigDecimal funding = null;
        BigDecimal commission = null;

        if (escrow) {
            WalletEscrowService.HoldResult hold = walletEscrow.hold(new EscrowHoldRequest(
                    tenantId, owner.getEmail(), totalAmount,
                    "Booking payment (held until after check-in) for listing " + listing.getTitle(),
                    reference, INITIATED_BY));
            outcome = hold.status();
            outcomeMessage = hold.message();
            funding = hold.totalDebited();
            commission = hold.commission();
        } else {
            WalletTransferService.Result transfer = walletTransfers.transfer(new WalletTransferRequest(
                    tenantId, owner.getEmail(), totalAmount,
                    "Booking payment for listing " + listing.getTitle(), reference, INITIATED_BY));
            outcome = transfer.status();
            outcomeMessage = transfer.message();
        }

        if (outcome == WalletTransferService.Status.PAID) {
            booking.setPaymentReference(reference);
            booking.setFundingAmount(funding);
            booking.setCommissionAmount(commission);
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setEscrowStatus(escrow ? EscrowStatus.HELD : EscrowStatus.NONE);
            booking = bookingRepository.save(booking);

            emailService.sendBookingConfirmedEmails(
                    tenant.getEmail(), tenant.getFullName(),
                    owner.getEmail(), owner.getFullName(),
                    listing.getTitle(), booking, escrow, escrowService.releaseDelayHours());

            log.info("Booking confirmed: id={}, listing={}, tenant={}, owner={}, amount={}, escrow={}",
                    booking.getId(), listing.getId(), tenantId, listing.getOwnerId(), totalAmount, escrow);

            return toResponse(booking, true,
                    "Booking confirmed! You have booked \"" + listing.getTitle() + "\"." + walletFeeNote(totalAmount, commission),
                    tenant, owner);
        }

        if (outcome == WalletTransferService.Status.DECLINED) {
            failBooking(booking);
            log.warn("Booking payment declined: id={}, reason={}", booking.getId(), outcomeMessage);
            return toResponse(booking, false, "Payment failed: " + outcomeMessage, tenant, owner);
        }

        // Unknown: the money may or may not have moved, so the booking stays PENDING with its nights held
        // and BookingReconciliationService settles it within a few minutes.
        log.warn("Booking payment outcome unknown, left PENDING for reconciliation: id={}, reason={}",
                booking.getId(), outcomeMessage);
        throw new WalletServiceUnavailableException(
                "We could not confirm your payment yet. Your dates are held for a few minutes while we check. "
                        + "If the payment went through you will receive a confirmation email.");
    }

    private void failBooking(Booking booking) {
        booking.setStatus(BookingStatus.FAILED);
        bookingRepository.save(booking);
        slotService.release(booking.getId());
    }

    private BookingResponse replay(Booking existing, BookingRequest request,
                                   UserSummaryResponse tenant, String authHeader) {
        boolean sameRequest = existing.getListingId().equals(request.getListingId())
                && existing.getCheckIn().equals(request.getCheckIn())
                && existing.getCheckOut().equals(request.getCheckOut())
                && existing.getPriceUnit() == request.getPriceUnit();
        if (!sameRequest) {
            throw new IllegalArgumentException(
                    "This Idempotency-Key was already used for a different booking request.");
        }
        UserSummaryResponse owner = authServiceClient.getUserSummary(existing.getOwnerId(), authHeader);
        String message = switch (existing.getStatus()) {
            case CONFIRMED, COMPLETED -> "This request was already processed. Returning the existing booking.";
            case PENDING -> "This request is still being processed.";
            case NEEDS_REVIEW -> "The payment for this request is being checked by our team.";
            default -> "This request was already processed and the booking did not go through. "
                    + "Use a new Idempotency-Key to try again.";
        };
        boolean ok = existing.getStatus() == BookingStatus.CONFIRMED || existing.getStatus() == BookingStatus.COMPLETED;
        return toResponse(existing, ok, message, tenant, owner);
    }

    private static String normaliseKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String trimmed = key.trim();
        if (trimmed.length() > 100) {
            throw new IllegalArgumentException("Idempotency-Key must be at most 100 characters.");
        }
        return trimmed;
    }

    private BigDecimal resolveRate(ShortTermRental listing, PriceUnit requested) {
        BigDecimal rate = switch (requested) {
            case PER_DAY -> listing.getPricePerDay();
            case PER_NIGHT -> listing.getPricePerNight();
            default -> null;
        };
        if (rate == null) {
            throw new IllegalArgumentException("This listing does not offer a " + requested + " rate.");
        }
        return rate;
    }

    // ====================== CANCEL ======================

    public BookingResponse cancelBooking(String bookingId, String userId, String authHeader, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        boolean isTenant = booking.getTenantId().equals(userId);
        boolean isOwner = booking.getOwnerId().equals(userId);
        if (!isTenant && !isOwner) {
            throw new AccessDeniedException("You can only cancel your own bookings.");
        }

        UserSummaryResponse tenant = authServiceClient.getUserSummary(booking.getTenantId(), authHeader);
        UserSummaryResponse owner = authServiceClient.getUserSummary(booking.getOwnerId(), authHeader);

        // A repeated cancel by the same person returns the earlier result instead of an error.
        if (booking.getStatus() == BookingStatus.CANCELLED && userId.equals(booking.getCancelledBy())) {
            return toResponse(booking, true, "This booking was already cancelled.", tenant, owner);
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only a confirmed booking can be cancelled.");
        }
        if (tenant == null || tenant.getEmail() == null) {
            throw new IllegalArgumentException("Could not resolve the tenant's account for refund.");
        }

        String listingTitle = listingTitle(booking.getListingId());

        if (booking.getEscrowStatus() == null || booking.getEscrowStatus() == EscrowStatus.NONE) {
            return cancelPaidDirectly(booking, userId, reason, tenant, owner, listingTitle);
        }

        if (booking.getEscrowStatus() == EscrowStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "The payment for this stay has already been paid out to the owner, so it can no longer be "
                            + "cancelled here. Please contact support.");
        }

        boolean cancelledByOwner = isOwner && !isTenant;
        CancellationQuoteResponse quote = policyService.quote(booking, cancelledByOwner, LocalDateTime.now());

        // Escrow payouts carry no fee, so each side gets exactly its share of the price. The two shares add up
        // to the price held. The fee charged when the tenant paid is not refunded.
        Booking cancelled = escrowService.markCancelled(booking.getId(), userId, reason,
                quote.getRefundPercent(), quote.getRefundAmount(), quote.getOwnerAmount());
        if (cancelled == null) {
            throw new IllegalArgumentException(
                    "This booking cannot be cancelled right now. It may already be cancelled, or a payout is in "
                            + "progress. Please try again in a few minutes.");
        }
        slotService.release(cancelled.getId());

        cancelled = escrowService.settleCancellation(cancelled);

        emailService.sendBookingCancelledEmails(
                tenant.getEmail(), tenant.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                listingTitle, cancelled, true);

        log.info("Booking cancelled: id={}, cancelledBy={}, refundPercent={}, escrow={}",
                cancelled.getId(), userId, quote.getRefundPercent(), cancelled.getEscrowStatus());

        return toResponse(cancelled, true, cancellationMessage(cancelled), tenant, owner);
    }

    private String cancellationMessage(Booking booking) {
        int percent = booking.getRefundPercent() != null ? booking.getRefundPercent() : 0;
        String amount = booking.getCurrency() + " " + booking.getRefundAmount();
        if (percent == 0) {
            return "Booking cancelled. No refund applies under the " + booking.getCancellationPolicy() + " policy.";
        }
        if (booking.getEscrowStatus() == EscrowStatus.SETTLED) {
            return "Booking cancelled. A refund of " + amount + " (" + percent
                    + "% of the price) has been credited to the wallet. The wallet fee paid when booking is not refunded.";
        }
        return "Booking cancelled. A refund of " + amount + " (" + percent
                + "% of the price) is being processed and will reach the wallet shortly. "
                + "The wallet fee paid when booking is not refunded.";
    }

    /** Bookings paid straight to the owner (escrow off, or made before escrow existed): full refund from the owner. */
    private BookingResponse cancelPaidDirectly(Booking booking, String userId, String reason,
                                               UserSummaryResponse tenant, UserSummaryResponse owner,
                                               String listingTitle) {
        String refundReference = "booking-refund-" + booking.getId();
        WalletTransferRequest refundRequest = new WalletTransferRequest(
                booking.getOwnerId(), tenant.getEmail(), booking.getTotalAmount(),
                "Refund for cancelled booking " + booking.getId(), refundReference, INITIATED_BY);

        WalletTransferService.Result refund = walletTransfers.transfer(refundRequest);
        if (!refund.paid()) {
            log.warn("Booking refund not completed: id={}, status={}, reason={}",
                    booking.getId(), refund.status(), refund.message());
            String why = refund.status() == WalletTransferService.Status.UNKNOWN
                    ? "The refund could not be confirmed yet. Please try again in a few minutes."
                    : refund.message();
            return toResponse(booking, false, "Cancellation failed. The refund could not be processed: " + why,
                    tenant, owner);
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledBy(userId);
        booking.setCancellationReason(reason);
        booking.setRefundReference(refundReference);
        booking.setRefundPercent(100);
        booking.setRefundAmount(booking.getTotalAmount());
        booking.setRefundSettled(true);
        booking = bookingRepository.save(booking);
        slotService.release(booking.getId());

        emailService.sendBookingCancelledEmails(
                tenant.getEmail(), tenant.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                listingTitle, booking, false);

        log.info("Booking cancelled (direct payment): id={}, cancelledBy={}", booking.getId(), userId);
        return toResponse(booking, true, "Booking cancelled and refund processed.", tenant, owner);
    }

    /**
     * Tells the tenant about the wallet fee added to their payment. Uses the fee wallet-service reported for an
     * escrow hold, otherwise works it out from the rate. Skipped quietly if neither is known.
     */
    private String walletFeeNote(BigDecimal amount, BigDecimal reportedFee) {
        try {
            BigDecimal fee = reportedFee != null ? reportedFee : commissionService.fee(amount);
            return " A wallet fee of USD " + fee + " was added to your payment.";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private String listingTitle(String listingId) {
        try {
            Object raw = listingService.getListingById(listingId);
            return (raw instanceof Listing l) ? l.getTitle() : "your listing";
        } catch (NotFoundException e) {
            return "the listing";
        }
    }

    /** What cancelling this booking would refund right now, for the caller's role. */
    public CancellationQuoteResponse getCancellationQuote(String bookingId, String userId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));
        boolean isTenant = booking.getTenantId().equals(userId);
        boolean isOwner = booking.getOwnerId().equals(userId);
        if (!isTenant && !isOwner) {
            throw new AccessDeniedException("You can only view your own bookings.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only a confirmed booking can be cancelled.");
        }
        if (booking.getEscrowStatus() == EscrowStatus.RELEASED) {
            throw new IllegalArgumentException(
                    "The payment for this stay has already been paid out to the owner, so it can no longer be cancelled here.");
        }
        boolean direct = booking.getEscrowStatus() == null || booking.getEscrowStatus() == EscrowStatus.NONE;
        CancellationQuoteResponse quote = policyService.quote(booking, direct || (isOwner && !isTenant), LocalDateTime.now());
        if (!direct) {
            quote.setMessage(quote.getMessage() + " The wallet fee paid when booking is not refunded.");
        }
        return quote;
    }

    // ====================== SCHEDULED: COMPLETE PAST BOOKINGS ======================

    /**
     * Marks CONFIRMED bookings as COMPLETED once check-out has passed. Housekeeping only:
     * escrow payouts do not depend on it. Runs on one instance at a time.
     */
    @Scheduled(cron = "${booking.completion-check-cron:0 */5 * * * *}")
    public void completeExpiredBookings() {
        lockService.runLocked("booking-completion", Duration.ofMinutes(4), Duration.ofSeconds(30), () -> {
            List<Booking> expired = bookingRepository.findByStatusAndCheckOutBefore(
                    BookingStatus.CONFIRMED, LocalDateTime.now());
            for (Booking booking : expired) {
                booking.setStatus(BookingStatus.COMPLETED);
                bookingRepository.save(booking);
            }
            if (!expired.isEmpty()) {
                log.info("Marked {} booking(s) as completed.", expired.size());
            }
        });
    }

    // ====================== QUERIES ======================

    public Page<BookingResponse> getMyBookingsAsTenant(String tenantId, BookingStatus status, String authHeader, Pageable pageable) {
        UserSummaryResponse tenant = authServiceClient.getCurrentUser(authHeader);
        if (tenant == null || !tenant.getId().equals(tenantId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        Map<String, UserSummaryResponse> ownerCache = new HashMap<>();
        Page<Booking> page = status != null
                ? bookingRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : bookingRepository.findByTenantId(tenantId, pageable);

        return page.map(booking -> {
            UserSummaryResponse owner = ownerCache.computeIfAbsent(
                    booking.getOwnerId(), id -> authServiceClient.getUserSummary(id, authHeader));
            return toResponse(booking, true, null, tenant, owner);
        });
    }

    public Page<BookingResponse> getMyBookingsAsOwner(String ownerId, BookingStatus status, String authHeader, Pageable pageable) {
        UserSummaryResponse owner = authServiceClient.getCurrentUser(authHeader);
        if (owner == null || !owner.getId().equals(ownerId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        Map<String, UserSummaryResponse> tenantCache = new HashMap<>();
        Page<Booking> page = status != null
                ? bookingRepository.findByOwnerIdAndStatus(ownerId, status, pageable)
                : bookingRepository.findByOwnerId(ownerId, pageable);

        return page.map(booking -> {
            UserSummaryResponse tenant = tenantCache.computeIfAbsent(
                    booking.getTenantId(), id -> authServiceClient.getUserSummary(id, authHeader));
            return toResponse(booking, true, null, tenant, owner);
        });
    }

    // ====================== HELPERS ======================

    private BookingResponse toResponse(Booking booking, boolean success, String message,
                                       UserSummaryResponse tenant, UserSummaryResponse owner) {
        return new BookingResponse(
                booking.getId(),
                booking.getListingId(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getPriceUnit(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                message,
                success,
                tenant,
                owner,
                booking.getCancellationPolicy(),
                booking.getEscrowStatus(),
                booking.getRefundPercent(),
                booking.getRefundAmount(),
                booking.getOwnerPayoutAmount(),
                booking.getRate(),
                booking.getUnits(),
                booking.getFundingAmount()
        );
    }
}
