package com.premisave.listing.controller;

import com.premisave.listing.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/listings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
public class AdminListingController {

    private final AdminService adminService;

    /**
     * Get all listings (for admin dashboard)
     */
    @GetMapping
    public ResponseEntity<List<Object>> getAllListings() {
        List<Object> listings = adminService.getAllListings();
        return ResponseEntity.ok(listings);
    }

    /**
     * Get a specific listing by ID (supports all listing types)
     */
    @GetMapping("/{id}")
    public ResponseEntity<Object> getListingForAdmin(@PathVariable String id) {
        Object listing = adminService.getListingById(id);
        return ResponseEntity.ok(listing);
    }

    /**
     * Approve a listing
     */
    @PutMapping("/{id}/approve")
    public ResponseEntity<String> approveListing(@PathVariable String id) {
        String message = adminService.approveListing(id);
        return ResponseEntity.ok(message);
    }

    /**
     * Reject a listing
     */
    @PutMapping("/{id}/reject")
    public ResponseEntity<String> rejectListing(@PathVariable String id) {
        String message = adminService.rejectListing(id);
        return ResponseEntity.ok(message);
    }

    /**
     * Archive a listing
     */
    @PutMapping("/{id}/archive")
    public ResponseEntity<String> archiveListing(@PathVariable String id) {
        String message = adminService.archiveListing(id);
        return ResponseEntity.ok(message);
    }
}