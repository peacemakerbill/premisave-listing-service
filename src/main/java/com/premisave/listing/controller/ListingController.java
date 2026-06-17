package com.premisave.listing.controller;

import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.service.ListingService;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final JwtUtil jwtUtil;

    // ====================== CRUD OPERATIONS ======================

    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody ListingRequest request,
            @RequestHeader("Authorization") String authorization) {

        ListingResponse response = listingService.createListing(request, authorization);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> getListingById(@PathVariable String id) {
        Object listing = listingService.getListingById(id);
        return ResponseEntity.ok(listing);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingResponse> updateListing(
            @PathVariable String id,
            @Valid @RequestBody ListingRequest request,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        ListingResponse response = listingService.updateListing(id, request, userId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteListing(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        String message = listingService.deleteListing(id, userId);
        return ResponseEntity.ok(message);
    }

    // ====================== QUERY ENDPOINTS ======================

    @GetMapping("/short-term")
    public ResponseEntity<List<ShortTermRental>> getShortTermRentals(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.getShortTermRentals(city));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<?>> getListingsByOwner(
            @PathVariable String ownerId,
            @RequestParam ListingCategory category) {
        return ResponseEntity.ok(listingService.getListingsByOwner(ownerId, category));
    }

    @GetMapping("/search")
    public ResponseEntity<List<?>> searchListings(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String city) {

        List<?> results = listingService.searchListings(query, category, minPrice, maxPrice, city);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/category/{category}")
    public ResponseEntity<List<?>> getListingsByCategory(
            @PathVariable ListingCategory category,
            @RequestParam(required = false) String city) {

        List<?> results = listingService.getListingsByCategory(category, city);
        return ResponseEntity.ok(results);
    }

    // ====================== IMAGE UPLOAD ======================

    @PostMapping("/upload-images")
    public ResponseEntity<List<String>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        List<String> imageUrls = listingService.uploadImages(files);
        return ResponseEntity.ok(imageUrls);
    }
}