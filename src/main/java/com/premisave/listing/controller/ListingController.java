package com.premisave.listing.controller;

import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.service.ListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;

    // ====================== LISTING CRUD ======================
    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody ListingRequest request,
            @RequestHeader("Authorization") String authorization) {

        ListingResponse response = listingService.createListing(request, authorization);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/short-term")
    public ResponseEntity<List<ShortTermRental>> getShortTermRentals(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.getShortTermRentals(city));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<?> getListingsByOwner(
            @PathVariable String ownerId,
            @RequestParam ListingCategory category) {
        return ResponseEntity.ok(listingService.getListingsByOwner(ownerId, category));
    }
}