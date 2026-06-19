package com.premisave.listing.controller;

import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.ListingCategoryRequest;
import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingUpdateRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.dto.MyListingResponse;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.service.AdPromotionService;
import com.premisave.listing.service.ListingService;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final AdPromotionService adPromotionService;
    private final JwtUtil jwtUtil;

    // ====================== CREATE LISTING ======================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ListingResponse> createListing(
            @RequestPart("request") @Valid ListingRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {

        List<MultipartFile> validFiles = files != null
                ? files.stream().filter(f -> f != null && !f.isEmpty()).toList()
                : List.of();

        if (!validFiles.isEmpty()) {
            List<String> imageUrls = listingService.uploadImages(validFiles);
            request.setImageUrls(imageUrls);
            if (!imageUrls.isEmpty() && (request.getMainImageUrl() == null || request.getMainImageUrl().isBlank())) {
                request.setMainImageUrl(imageUrls.get(0));
            }
        } else {
            if (request.getImageUrls() == null) {
                request.setImageUrls(new ArrayList<>());
            }
        }

        ListingResponse response = listingService.createListing(request, authorization);
        return ResponseEntity.ok(response);
    }

    // ====================== UPDATE LISTING ======================

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ListingResponse> updateListing(
            @PathVariable String id,
            @RequestPart("request") @Valid ListingUpdateRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);

        List<MultipartFile> validFiles = files != null
                ? files.stream().filter(f -> f != null && !f.isEmpty()).toList()
                : List.of();

        if (!validFiles.isEmpty()) {
            List<String> newImageUrls = listingService.uploadImages(validFiles);
            if (request.getImageUrls() == null) {
                request.setImageUrls(new ArrayList<>());
            }
            request.getImageUrls().addAll(newImageUrls);
            if (request.getMainImageUrl() == null || request.getMainImageUrl().isBlank()) {
                request.setMainImageUrl(newImageUrls.get(0));
            }
        }

        ListingResponse response = listingService.updateListing(id, request, userId);
        return ResponseEntity.ok(response);
    }

    // ====================== OTHER ENDPOINTS ======================

    @GetMapping("/{id}")
    public ResponseEntity<Object> getListingById(@PathVariable String id) {
        return ResponseEntity.ok(listingService.getListingById(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<String> deleteListing(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.deleteListing(id, userId));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<String> archiveListing(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.archiveListing(id, userId));
    }

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<String> unarchiveListing(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.unarchiveListing(id, userId));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<List<MyListingResponse>> getMyListings(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) ListingStatus status) {

        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.getMyListings(ownerId, status));
    }

    // ====================== PROMOTION ======================

    /**
     * Promote a listing.
     *
     * POST /listings/promote?method=MPESA
     *
     * A listing cannot be promoted if it already has an active promotion.
     * Use /listings/{id}/extend to add more days to an existing promotion.
     *
     * @param method payment method (defaults to MPESA)
     */
    @PostMapping("/promote")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<AdPromotionResponse> promoteListing(
            @Valid @RequestBody AdPromotionRequest request,
            @RequestParam(defaultValue = "MPESA") PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        AdPromotionResponse response = adPromotionService.promoteListing(request, userId, authorization, method);
        return ResponseEntity.ok(response);
    }

    /**
     * Extend an existing promotion.
     *
     * POST /listings/{id}/extend?days=7&method=MPESA
     *
     * Can be called even while the promotion is still active —
     * days are added on top of the current end date.
     *
     * @param method payment method (defaults to MPESA)
     */
    @PostMapping("/{id}/extend")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<AdPromotionResponse> extendPromotion(
            @PathVariable String id,
            @RequestParam int days,
            @RequestParam(defaultValue = "MPESA") PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        AdPromotionResponse response = adPromotionService.extendPromotion(id, days, userId, authorization, method);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/promotions/my")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<List<com.premisave.listing.entity.ListingPromotion>> getMyPromotions(
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(adPromotionService.getUserPromotions(userId));
    }

    // ====================== DISCOVERY ======================

    @GetMapping("/short-term")
    public ResponseEntity<List<ShortTermRental>> getShortTermRentals(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.getShortTermRentals(city));
    }

    @PostMapping("/category")
    public ResponseEntity<List<?>> getListingsByCategory(
            @Valid @RequestBody ListingCategoryRequest request) {
        return ResponseEntity.ok(listingService.getListingsByCategory(
                request.getCategory(), request.getCity()));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<?>> getListingsByOwner(
            @PathVariable String ownerId,
            @RequestParam(required = false) ListingCategory category) {
        return ResponseEntity.ok(listingService.getListingsByOwner(ownerId, category));
    }

    @GetMapping("/search")
    public ResponseEntity<List<?>> searchListings(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.searchListings(query, category, minPrice, maxPrice, city));
    }

    @PostMapping("/upload-images")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<List<String>> uploadImages(
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {
        return ResponseEntity.ok(listingService.uploadImages(files));
    }
}