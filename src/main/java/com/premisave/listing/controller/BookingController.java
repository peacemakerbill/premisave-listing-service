package com.premisave.listing.controller;

import com.premisave.listing.dto.BookingRequest;
import com.premisave.listing.dto.BookingResponse;
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
     * Book a short-term rental — pays the owner directly via a
     * wallet-service transfer. Requires the caller to actually be the
     * authenticated user making the request (checked in BookingService).
     */
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader("Authorization") String authorization) {
        String tenantId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.createBooking(request, tenantId, authorization));
    }

    /**
     * Cancel a confirmed booking — refunds the tenant via a wallet-service
     * transfer. Either the tenant or the listing owner can cancel.
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
     * The caller's own bookings as a tenant, optionally filtered by status
     * (e.g. ?status=CONFIRMED for active bookings, ?status=CANCELLED for
     * cancelled ones). Each item includes live tenant/owner details fetched
     * from auth-service.
     */
    @GetMapping("/me")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String tenantId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.getMyBookingsAsTenant(tenantId, status, authorization, pageable));
    }

    /**
     * Bookings received on the caller's own listings (as owner), optionally
     * filtered by status.
     */
    @GetMapping("/received")
    public ResponseEntity<Page<BookingResponse>> getReceivedBookings(
            @RequestParam(required = false) BookingStatus status,
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(bookingService.getMyBookingsAsOwner(ownerId, status, authorization, pageable));
    }
}