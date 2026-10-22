package com.premisave.listing.controller;

import com.premisave.listing.dto.AvailabilityResponse;
import com.premisave.listing.dto.BlockDatesRequest;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.service.BookingSlotService;
import com.premisave.listing.service.ListingService;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Which nights of a short-term listing can be booked, and the owner's own
 * blocked dates (maintenance, personal use). Blocking uses the same
 * per-night claims as bookings, so a blocked night can never be booked and
 * a booked night can never be blocked.
 */
@RestController
@RequestMapping("/listings/{listingId}")
@RequiredArgsConstructor
public class AvailabilityController {

    private static final int MAX_RANGE_DAYS = 366;

    private final BookingSlotService slotService;
    private final ListingService listingService;
    private final JwtUtil jwtUtil;

    /** Public. Defaults to the next 90 days. */
    @GetMapping("/availability")
    public ResponseEntity<AvailabilityResponse> availability(
            @PathVariable String listingId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        requireShortTermRental(listingId);
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(90);
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("to must not be before from.");
        }
        if (start.plusDays(MAX_RANGE_DAYS).isBefore(end)) {
            throw new IllegalArgumentException("The range can be at most " + MAX_RANGE_DAYS + " days.");
        }
        return ResponseEntity.ok(new AvailabilityResponse(
                listingId, start, end, slotService.unavailableDays(listingId, start, end)));
    }

    @PostMapping("/blocked-dates")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<Map<String, String>> blockDates(
            @PathVariable String listingId,
            @Valid @RequestBody BlockDatesRequest request,
            @RequestHeader("Authorization") String authorization) {

        requireOwner(listingId, jwtUtil.extractUserId(authorization));
        if (!request.getTo().isAfter(request.getFrom())) {
            throw new IllegalArgumentException("to must be after from.");
        }
        if (request.getFrom().plusDays(MAX_RANGE_DAYS).isBefore(request.getTo())) {
            throw new IllegalArgumentException("A block can be at most " + MAX_RANGE_DAYS + " days.");
        }
        if (request.getTo().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Blocked dates must not be entirely in the past.");
        }
        String blockId = UUID.randomUUID().toString().replace("-", "");
        slotService.claimBlock(listingId, blockId, request.getFrom(), request.getTo());
        return ResponseEntity.ok(Map.of("blockId", blockId, "message", "Dates blocked."));
    }

    @DeleteMapping("/blocked-dates/{blockId}")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<Map<String, String>> unblockDates(
            @PathVariable String listingId,
            @PathVariable String blockId,
            @RequestHeader("Authorization") String authorization) {

        requireOwner(listingId, jwtUtil.extractUserId(authorization));
        if (!slotService.blockExists(listingId, blockId)) {
            throw new NotFoundException("Blocked dates not found");
        }
        slotService.release(BookingSlotService.BLOCK_PREFIX + blockId);
        return ResponseEntity.ok(Map.of("message", "Dates unblocked."));
    }

    private ShortTermRental requireShortTermRental(String listingId) {
        Object raw = listingService.getListingById(listingId);
        if (!(raw instanceof ShortTermRental listing)) {
            throw new IllegalArgumentException("Availability is only tracked for short-term rental listings.");
        }
        return listing;
    }

    private void requireOwner(String listingId, String userId) {
        ShortTermRental listing = requireShortTermRental(listingId);
        if (!listing.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("You can only manage dates on your own listings");
        }
    }
}
