package com.premisave.listing.controller;

import com.premisave.listing.entity.Booking;
import com.premisave.listing.service.BookingAdminService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {

    private final BookingAdminService adminService;
    private final JwtUtil jwtUtil;

    /** Bookings the automatic jobs gave up on: unconfirmed payments and failed payouts. */
    @GetMapping("/needs-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Page<Booking>> needsReview(Pageable pageable) {
        return ResponseEntity.ok(adminService.needsReview(pageable));
    }

    /** Re-queues a payout that was parked as NEEDS_REVIEW. */
    @PostMapping("/{id}/retry-payout")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Booking> retryPayout(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(adminService.retryPayout(id, jwtUtil.extractUserId(authorization)));
    }

    /**
     * Settles a booking whose payment could not be confirmed, after checking
     * wallet-service's history for reference "booking-{id}".
     * paid=true confirms the booking; paid=false marks it failed and frees its dates.
     */
    @PostMapping("/{id}/resolve-payment")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Booking> resolvePayment(
            @PathVariable String id,
            @RequestParam boolean paid,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(adminService.resolvePayment(id, paid, jwtUtil.extractUserId(authorization)));
    }
}
