package com.premisave.listing.controller;

import com.premisave.listing.entity.ListingSettings;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.service.AdminService;
import com.premisave.listing.service.ListingSettingsService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/listings")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminService adminService;
    private final ListingSettingsService settingsService;
    private final JwtUtil jwtUtil;

    // ====================== SETTINGS ======================

    /** The marketplace settings, including whether new listings need an admin's review. */
    @GetMapping("/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<ListingSettings> settings() {
        return ResponseEntity.ok(settingsService.get());
    }

    /** Turns the review of new listings on or off. Body: { "reviewRequired": true }. */
    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ListingSettings> updateSettings(
            @RequestBody Map<String, Object> body,
            @RequestHeader("Authorization") String authorization) {
        boolean required = Boolean.parseBoolean(String.valueOf(body.get("reviewRequired")));
        return ResponseEntity.ok(settingsService.setReviewRequired(required, jwtUtil.extractUserId(authorization)));
    }

    // ====================== READ ======================

    /**
     * Get all listings with optional filters, paginated (page/size/sort
     * query params are resolved automatically into Pageable). Previously
     * returned every matching listing in one unbounded response.
     * ADMIN and FINANCE roles allowed.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Page<Object>> getAllListings(
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) ListingStatus status,
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllListings(deleted, archived, status, pageable));
    }

    /**
     * Get a specific listing by ID.
     * ADMIN and FINANCE roles allowed.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<Object> getListingById(@PathVariable String id) {
        return ResponseEntity.ok(adminService.getListingById(id));
    }

    // ====================== APPROVE / REJECT ======================

    /**
     * Approve a pending listing.
     * ADMIN role only.
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> approveListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.approveListing(id));
    }

    /**
     * Reject a listing.
     * ADMIN role only.
     */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> rejectListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.rejectListing(id));
    }

    // ====================== ARCHIVE / UNARCHIVE ======================

    /**
     * Archive a listing.
     * ADMIN role only.
     */
    @PutMapping("/{id}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> archiveListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.archiveListing(id));
    }

    /**
     * Unarchive a listing.
     * ADMIN role only.
     */
    @PutMapping("/{id}/unarchive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> unarchiveListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.unarchiveListing(id));
    }

    // ====================== DELETE / RESTORE ======================

    /**
     * Soft delete a listing.
     * ADMIN role only.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> softDeleteListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.softDeleteListing(id));
    }

    /**
     * Restore a soft-deleted listing.
     * ADMIN role only.
     */
    @PutMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> restoreListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.restoreListing(id));
    }

    /**
     * Permanently delete a listing.
     * ADMIN role only.
     */
    @DeleteMapping("/{id}/hard-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> hardDeleteListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.hardDeleteListing(id));
    }
}