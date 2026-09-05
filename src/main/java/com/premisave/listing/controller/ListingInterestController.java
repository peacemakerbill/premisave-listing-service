package com.premisave.listing.controller;

import com.premisave.listing.dto.ApiResponse;
import com.premisave.listing.dto.ExpressInterestRequest;
import com.premisave.listing.dto.InterestResponse;
import com.premisave.listing.service.ListingInterestService;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interests")
@RequiredArgsConstructor
public class ListingInterestController {

    private final ListingInterestService interestService;
    private final JwtUtil jwtUtil;

    /**
     * Express interest in a listing (long-term rental, lease, house sale,
     * land sale) — no money involved. The customer's contact details are
     * NOT submitted here; they're fetched from auth-service using the
     * authenticated userId.
     */
    @PostMapping
    public ResponseEntity<InterestResponse> expressInterest(
            @Valid @RequestBody ExpressInterestRequest request,
            @RequestHeader("Authorization") String authorization) {
        String customerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(interestService.expressInterest(request, customerId, authorization));
    }

    /**
     * Cancel (permanently delete) a previously expressed interest.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> cancelInterest(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {
        String customerId = jwtUtil.extractUserId(authorization);
        interestService.cancelInterest(id, customerId, authorization);
        return ResponseEntity.ok(new ApiResponse<>(true, "Interest cancelled.", null));
    }

    /** The caller's own expressed interests (as a customer). */
    @GetMapping("/me")
    public ResponseEntity<Page<InterestResponse>> getMyInterests(
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String customerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(interestService.getMyInterests(customerId, authorization, pageable));
    }

    /** Leads received on the caller's own listings (as owner). */
    @GetMapping("/received")
    public ResponseEntity<Page<InterestResponse>> getReceivedInterests(
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(interestService.getInterestsForMyListings(ownerId, authorization, pageable));
    }
}