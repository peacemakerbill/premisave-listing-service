package com.premisave.listing.controller;

import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/listings")
@RequiredArgsConstructor
public class AdminListingController {

    private final AdminService adminService;

    // ====================== READ ======================

    /**
     * Get all listings with optional filters.
     * ADMIN and FINANCE roles allowed.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
    public ResponseEntity<List<Object>> getAllListings(
            @RequestParam(required = false) Boolean deleted,
            @RequestParam(required = false) Boolean archived,
            @RequestParam(required = false) ListingStatus status) {
        return ResponseEntity.ok(adminService.getAllListings(deleted, archived, status));
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