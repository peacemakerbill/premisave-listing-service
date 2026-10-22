package com.premisave.listing.controller;

import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.ImageListResponse;
import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingUpdateRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.dto.MyListingResponse;
import com.premisave.listing.dto.ReorderImagesRequest;
import com.premisave.listing.dto.SetCoverImageRequest;
import com.premisave.listing.entity.ListingPromotion;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.service.AdPromotionService;
import com.premisave.listing.service.ImageStorageService;
import com.premisave.listing.service.ListingService;
import com.premisave.listing.storage.StoredImage;
import com.premisave.listing.util.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final ImageStorageService imageStorage;
    private final AdPromotionService adPromotionService;
    private final JwtUtil jwtUtil;

    // ====================== CREATE LISTING ======================

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ListingResponse> createListing(
            @RequestPart("request") @Valid ListingRequest request,
            @RequestPart(value = "files", required = false) List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        List<MultipartFile> validFiles = nonEmpty(files);
        checkImageCount(validFiles.size());

        List<StoredImage> uploaded = List.of();
        try {
            if (!validFiles.isEmpty()) {
                uploaded = imageStorage.storeAll(validFiles, userId);
                List<String> imageUrls = new ArrayList<>(uploaded.stream().map(StoredImage::url).toList());
                request.setImageUrls(imageUrls);
                if (request.getMainImageUrl() == null || request.getMainImageUrl().isBlank()) {
                    request.setMainImageUrl(imageUrls.get(0));
                }
            } else if (request.getImageUrls() == null) {
                request.setImageUrls(new ArrayList<>());
            }
            checkImageCount(request.getImageUrls().size());
            imageStorage.assertOwned(request.getImageUrls(), userId);
            if (request.getMainImageUrl() != null) {
                imageStorage.assertOwned(List.of(request.getMainImageUrl()), userId);
            }
            return ResponseEntity.ok(listingService.createListing(request, authorization));
        } catch (RuntimeException e) {
            // The listing was not created, so do not leave the new pictures behind.
            uploaded.forEach(u -> imageStorage.delete(u.fileId()));
            throw e;
        }
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
        List<MultipartFile> validFiles = nonEmpty(files);
        checkImageCount(validFiles.size());

        List<StoredImage> uploaded = List.of();
        try {
            uploaded = imageStorage.storeAll(validFiles, userId);
            List<String> addedUrls = uploaded.stream().map(StoredImage::url).toList();
            return ResponseEntity.ok(listingService.updateListing(id, request, userId, addedUrls));
        } catch (RuntimeException e) {
            uploaded.forEach(u -> imageStorage.delete(u.fileId()));
            throw e;
        }
    }

    // ====================== IMAGES ======================

    @PostMapping(value = "/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ImageListResponse> addImages(
            @PathVariable String id,
            @RequestParam List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.addImages(id, files, userId));
    }

    @DeleteMapping("/{id}/images/{fileId}")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ImageListResponse> removeImage(
            @PathVariable String id,
            @PathVariable String fileId,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.removeImage(id, fileId, userId));
    }

    @PutMapping("/{id}/images/cover")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ImageListResponse> setCoverImage(
            @PathVariable String id,
            @RequestBody @Valid SetCoverImageRequest body,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.setCoverImage(id, body.fileId(), userId));
    }

    @PutMapping("/{id}/images/order")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<ImageListResponse> reorderImages(
            @PathVariable String id,
            @RequestBody @Valid ReorderImagesRequest body,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.reorderImages(id, body.fileIds(), userId));
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

    /**
     * Filterable by category, city, price range, and free-text query, on
     * top of the existing status filter — previously this only accepted
     * status, so checking "my luxury Nairobi listings under 20k" meant
     * fetching everything and filtering client-side.
     */
    @GetMapping("/me")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<List<MyListingResponse>> getMyListings(
            @RequestHeader("Authorization") String authorization,
            @RequestParam(required = false) ListingStatus status,
            @RequestParam(required = false) ListingCategory category,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) String query) {

        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(listingService.getMyListings(ownerId, status, category, city, minPrice, maxPrice, query));
    }

    // ====================== PROMOTION ======================

    /**
     * Promote a listing.
     *
     * POST /listings/promote
     *
     * A listing cannot be promoted if it already has an active promotion.
     * Use /listings/{id}/extend to add more days to an existing promotion.
     *
     * NOTE: the `method` parameter (PAYPAL/STRIPE/MPESA/AIRTEL_MONEY) is
     * gone — payment now always goes through a wallet-service debit, which
     * abstracts the funding provider away entirely.
     */
    @PostMapping("/promote")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<AdPromotionResponse> promoteListing(
            @Valid @RequestBody AdPromotionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        AdPromotionResponse response = adPromotionService.promoteListing(request, userId, authorization, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Extend an existing promotion.
     *
     * POST /listings/{id}/extend?days=7
     *
     * Can be called even while the promotion is still active —
     * days are added on top of the current end date.
     */
    @PostMapping("/{id}/extend")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<AdPromotionResponse> extendPromotion(
            @PathVariable String id,
            @RequestParam int days,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader("Authorization") String authorization) {

        String userId = jwtUtil.extractUserId(authorization);
        AdPromotionResponse response = adPromotionService.extendPromotion(id, days, userId, authorization, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    /**
     * Paginated — previously returned every promotion a user ever had in
     * one unbounded response.
     */
    @GetMapping("/promotions/my")
    @PreAuthorize("hasRole('HOME_OWNER')")
    public ResponseEntity<Page<ListingPromotion>> getMyPromotions(
            @RequestHeader("Authorization") String authorization,
            Pageable pageable) {
        String userId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(adPromotionService.getUserPromotions(userId, pageable));
    }

    // ====================== DISCOVERY ======================

    @GetMapping("/short-term")
    public ResponseEntity<List<ShortTermRental>> getShortTermRentals(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.getShortTermRentals(city));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<?>> getListingsByOwner(
            @PathVariable String ownerId,
            @RequestParam(required = false) ListingCategory category) {
        return ResponseEntity.ok(listingService.getListingsByOwner(ownerId, category));
    }

    /**
     * Filters by any combination of free-text query, category, city, and
     * price range. Replaces the old separate POST /listings/category
     * endpoint entirely — that was just this same search with the other
     * filters left blank, so keeping both was redundant. Only ever returns
     * publicly visible (promoted, non-expired, non-rejected) listings.
     */
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
            @RequestParam List<MultipartFile> files,
            @RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        List<MultipartFile> validFiles = nonEmpty(files);
        checkImageCount(validFiles.size());
        return ResponseEntity.ok(listingService.uploadImages(validFiles, userId));
    }

    // ====================== HELPERS ======================

    private static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private void checkImageCount(int count) {
        if (count > imageStorage.maxImagesPerListing()) {
            throw new IllegalArgumentException(
                    "A listing can have at most " + imageStorage.maxImagesPerListing() + " images.");
        }
    }
}