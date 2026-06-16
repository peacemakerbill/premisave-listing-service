package com.premisave.listing.controller;

import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Subscription> createSubscription(
            @RequestParam SubscriptionPlan plan,
            @RequestHeader("Authorization") String authorization) {
        // Extract userId from token in real implementation
        String ownerId = "current-user-id-from-jwt"; // TODO: Extract from JWT
        Subscription subscription = subscriptionService.createSubscription(ownerId, plan);
        return ResponseEntity.ok(subscription);
    }
}