package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.client.WalletServiceClient;
import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.BookingRequest;
import com.premisave.listing.dto.BookingResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.dto.wallet_service.WalletTransferRequest;
import com.premisave.listing.entity.Booking;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.enums.PriceUnit;
import com.premisave.listing.exception.AuthenticationFailedException;
import com.premisave.listing.exception.BookingConflictException;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import com.premisave.listing.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Handles short-term rental bookings: creating one (which pays the owner
 * directly, tenant -> owner, via wallet-service's transfer endpoint — a
 * different wallet-service operation from AdPromotionService's "debit for
 * a service charge"), and cancelling one (which refunds the tenant,
 * owner -> tenant).
 *
 * ASSUMPTION carried from WalletTransferRequest: recipientAccountNumber is
 * the recipient's email, resolved via AuthServiceClient. If wallet-service
 * actually expects something else, only resolveRate()/the two transfer
 * call sites below need to change, not the overall flow.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingService {

    private static final String INITIATED_BY = "LISTING_SERVICE";

    private final BookingRepository bookingRepository;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;
    private final WalletServiceClient walletServiceClient;
    private final EmailService emailService;

    // ====================== CREATE (PAY) ======================

    @Transactional
    public BookingResponse createBooking(BookingRequest request, String tenantId, String authHeader) {
        if (!request.getCheckOut().isAfter(request.getCheckIn())) {
            throw new IllegalArgumentException("checkOut must be after checkIn.");
        }

        UserSummaryResponse tenant = authServiceClient.getCurrentUser(authHeader);
        if (tenant == null || !tenant.getId().equals(tenantId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        Object rawListing = listingService.getListingById(request.getListingId());
        if (!(rawListing instanceof ShortTermRental listing)) {
            throw new IllegalArgumentException("Bookings are only supported for short-term rental listings.");
        }

        BigDecimal rate = resolveRate(listing, request.getPriceUnit());
        long units = ChronoUnit.DAYS.between(request.getCheckIn(), request.getCheckOut());
        if (units < 1) {
            throw new IllegalArgumentException("Booking must span at least one day/night.");
        }
        BigDecimal totalAmount = rate.multiply(BigDecimal.valueOf(units));

        List<Booking> conflicts = bookingRepository.findOverlapping(
                listing.getId(), request.getCheckIn(), request.getCheckOut());
        if (!conflicts.isEmpty()) {
            throw new BookingConflictException(
                    "This listing is already booked for part or all of the requested dates.");
        }

        UserSummaryResponse owner = authServiceClient.getUserSummary(listing.getOwnerId(), authHeader);
        if (owner == null || owner.getEmail() == null) {
            throw new IllegalArgumentException("Could not resolve the listing owner's account for payment.");
        }

        Booking booking = new Booking();
        booking.setListingId(listing.getId());
        booking.setTenantId(tenantId);
        booking.setOwnerId(listing.getOwnerId());
        booking.setCheckIn(request.getCheckIn());
        booking.setCheckOut(request.getCheckOut());
        booking.setPriceUnit(request.getPriceUnit());
        booking.setTotalAmount(totalAmount);
        booking.setStatus(BookingStatus.PENDING);
        booking = bookingRepository.save(booking);

        String reference = "booking-" + booking.getId();
        WalletTransferRequest transferRequest = new WalletTransferRequest(
                tenantId, owner.getEmail(), totalAmount,
                "Booking payment for listing " + listing.getTitle(),
                reference, INITIATED_BY);

        try {
            ApiResponse<Object> response = walletServiceClient.transferFunds(transferRequest);
            if (response != null && response.isSuccess()) {
                booking.setPaymentReference(reference);
                booking.setStatus(BookingStatus.CONFIRMED);
                booking = bookingRepository.save(booking);

                emailService.sendBookingConfirmedEmails(
                        tenant.getEmail(), tenant.getFullName(),
                        owner.getEmail(), owner.getFullName(),
                        listing.getTitle(), booking);

                log.info("Booking confirmed: id={}, listing={}, tenant={}, owner={}, amount={}",
                        booking.getId(), listing.getId(), tenantId, listing.getOwnerId(), totalAmount);

                return toResponse(booking, true, "Booking confirmed! You have booked \"" + listing.getTitle() + "\".");
            } else {
                booking.setStatus(BookingStatus.FAILED);
                bookingRepository.save(booking);
                String reason = response != null ? response.getMessage() : "no response from wallet-service";
                log.warn("Booking payment failed: id={}, reason={}", booking.getId(), reason);
                return toResponse(booking, false, "Payment failed: " + reason);
            }
        } catch (WalletServiceUnavailableException e) {
            booking.setStatus(BookingStatus.FAILED);
            bookingRepository.save(booking);
            log.warn("Booking payment failed — service unavailable: id={}, reason={}", booking.getId(), e.getMessage());
            throw e;
        }
    }

    private BigDecimal resolveRate(ShortTermRental listing, PriceUnit requested) {
        BigDecimal rate = switch (requested) {
            case PER_DAY -> listing.getPricePerDay();
            case PER_NIGHT -> listing.getPricePerNight();
            default -> null;
        };
        if (rate == null) {
            throw new IllegalArgumentException(
                    "This listing does not offer a " + requested + " rate.");
        }
        return rate;
    }

    // ====================== CANCEL (REFUND) ======================

    @Transactional
    public BookingResponse cancelBooking(String bookingId, String userId, String authHeader, String reason) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NotFoundException("Booking not found"));

        boolean isTenant = booking.getTenantId().equals(userId);
        boolean isOwner = booking.getOwnerId().equals(userId);
        if (!isTenant && !isOwner) {
            throw new AccessDeniedException("You can only cancel your own bookings.");
        }
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalArgumentException("Only a confirmed booking can be cancelled.");
        }

        UserSummaryResponse tenant = authServiceClient.getUserSummary(booking.getTenantId(), authHeader);
        UserSummaryResponse owner = authServiceClient.getUserSummary(booking.getOwnerId(), authHeader);
        if (tenant == null || tenant.getEmail() == null) {
            throw new IllegalArgumentException("Could not resolve the tenant's account for refund.");
        }

        String listingTitle;
        try {
            Object rawListing = listingService.getListingById(booking.getListingId());
            listingTitle = (rawListing instanceof com.premisave.listing.entity.Listing l) ? l.getTitle() : "your listing";
        } catch (NotFoundException e) {
            listingTitle = "the listing";
        }

        String refundReference = "booking-refund-" + booking.getId();
        WalletTransferRequest refundRequest = new WalletTransferRequest(
                booking.getOwnerId(), tenant.getEmail(), booking.getTotalAmount(),
                "Refund for cancelled booking " + booking.getId(),
                refundReference, INITIATED_BY);

        try {
            ApiResponse<Object> response = walletServiceClient.transferFunds(refundRequest);
            if (response == null || !response.isSuccess()) {
                // Refund didn't succeed — do NOT mark the booking cancelled,
                // since the tenant hasn't actually gotten their money back.
                String reasonMsg = response != null ? response.getMessage() : "no response from wallet-service";
                log.warn("Booking refund failed: id={}, reason={}", booking.getId(), reasonMsg);
                return toResponse(booking, false, "Cancellation failed — refund could not be processed: " + reasonMsg);
            }
        } catch (WalletServiceUnavailableException e) {
            log.warn("Booking refund failed — service unavailable: id={}, reason={}", booking.getId(), e.getMessage());
            throw e;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancelledAt(LocalDateTime.now());
        booking.setCancelledBy(userId);
        booking.setCancellationReason(reason);
        booking.setRefundReference(refundReference);
        booking = bookingRepository.save(booking);

        emailService.sendBookingCancelledEmails(
                tenant.getEmail(), tenant.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                listingTitle, booking);

        log.info("Booking cancelled: id={}, cancelledBy={}", booking.getId(), userId);

        return toResponse(booking, true, "Booking cancelled and refund processed.");
    }

    // ====================== QUERIES ======================

    public Page<Booking> getMyBookingsAsTenant(String tenantId, BookingStatus status, Pageable pageable) {
        return status != null
                ? bookingRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                : bookingRepository.findByTenantId(tenantId, pageable);
    }

    public Page<Booking> getMyBookingsAsOwner(String ownerId, BookingStatus status, Pageable pageable) {
        return status != null
                ? bookingRepository.findByOwnerIdAndStatus(ownerId, status, pageable)
                : bookingRepository.findByOwnerId(ownerId, pageable);
    }

    // ====================== HELPERS ======================

    private BookingResponse toResponse(Booking booking, boolean success, String message) {
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
                success
        );
    }
}