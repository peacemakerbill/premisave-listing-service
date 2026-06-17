package com.premisave.listing.controller;

import com.premisave.listing.service.MpesaC2BService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/payments/mpesa")
@RequiredArgsConstructor
public class MpesaC2BController {

    private final MpesaC2BService mpesaC2BService;

    /**
     * Register C2B URLs (Confirmation & Validation URLs)
     * This should be called once during setup or after deployment
     * Recommended: Protected for ADMIN only
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/c2b/register-urls")
    public ResponseEntity<String> registerC2BUrls() {
        try {
            mpesaC2BService.registerC2BUrls();
            return ResponseEntity.ok("M-Pesa C2B URLs registered successfully");
        } catch (Exception e) {
            log.error("Failed to register C2B URLs", e);
            return ResponseEntity.internalServerError()
                    .body("Failed to register C2B URLs: " + e.getMessage());
        }
    }

    /**
     * Health check for M-Pesa C2B endpoints
     */
    @GetMapping("/c2b/status")
    public ResponseEntity<String> c2bStatus() {
        return ResponseEntity.ok("M-Pesa C2B endpoints are active");
    }
}