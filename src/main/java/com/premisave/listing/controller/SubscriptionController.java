package com.premisave.listing.controller;

import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.service.SubscriptionService;
import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Subscription> createSubscription(
            @RequestParam SubscriptionPlan plan,
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        
        Subscription subscription = subscriptionService.createSubscription(ownerId, plan);
        return ResponseEntity.ok(subscription);
    }

    @GetMapping("/me")
    public ResponseEntity<List<Subscription>> getMySubscriptions(
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        List<Subscription> subscriptions = subscriptionService.getUserSubscriptions(ownerId);
        return ResponseEntity.ok(subscriptions);
    }

    @GetMapping("/active")
    public ResponseEntity<Subscription> getActiveSubscription(
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        Subscription subscription = subscriptionService.getActiveSubscription(ownerId);
        return ResponseEntity.ok(subscription);
    }
}