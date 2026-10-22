package com.premisave.listing.controller;

import com.premisave.listing.dto.BookingRequest;
import com.premisave.listing.dto.BookingResponse;
import com.premisave.listing.dto.CancellationQuoteResponse;
import com.premisave.listing.enums.BookingStatus;
import com.premisave.listing.service.BookingService;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final JwtUtil jwtUtil;

    /**
     * Book a short-term rental. The payment is held in escrow and paid to the
     * owner after check-in (or goes straight to the owner when escrow is
     * off). Send an Idempotency-Key header (any unique string, up to 100
     * characters) so a retry after a timeout or double tap returns the first
     * booking instead of creating a second one.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("Authorization") String authorization) {
        String tenantId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.createBooking(request, tenantId, authorization, idempotencyKey));
    }

    /**
     * Cancel a confirmed booking. The refund follows the listing's
     * cancellation policy; if the owner cancels, the tenant is refunded in
     * full. Cancelling twice returns the first result.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable String id,
            @RequestParam(required = false) String reason,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.cancelBooking(id, userId, authorization, reason));
    }

    /**
     * What cancelling this booking would refund right now, for the caller
     * (the tenant sees the policy refund, the owner sees a full refund).
     * Nothing changes.
     */
    @GetMapping("/{id}/cancellation-quote")
    public ResponseEntity<CancellationQuoteResponse> getCancellationQuote(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.getCancellationQuote(id, userId));
    }

    /**
     * The caller's own bookings as a tenant, optionally filtered by status
     * (e.g. ?status=CONFIRMED for active bookings, ?status=CANCELLED for
     * cancelled ones).
     */
    @GetMapping("/me")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String tenantId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.getMyBookingsAsTenant(tenantId, status, authorization, pageable));
    }

    /** Bookings received on the caller's own listings (as owner), optionally filtered by status. */
    @GetMapping("/received")
    public ResponseEntity<Page<BookingResponse>> getReceivedBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.getMyBookingsAsOwner(ownerId, status, authorization, pageable));
    }
}
