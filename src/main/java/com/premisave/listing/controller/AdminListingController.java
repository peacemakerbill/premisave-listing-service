package com.premisave.listing.controller;

import com.premisave.listing.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/listings")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'FINANCE')")
public class AdminListingController {

    private final AdminService adminService;

    @GetMapping
    public ResponseEntity<?> getAllListings() {
        return ResponseEntity.ok(adminService.getAllListings());
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<String> approveListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.approveListing(id));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<String> rejectListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.rejectListing(id));
    }

    @PutMapping("/{id}/archive")
    public ResponseEntity<String> archiveListing(@PathVariable String id) {
        return ResponseEntity.ok(adminService.archiveListing(id));
    }
}