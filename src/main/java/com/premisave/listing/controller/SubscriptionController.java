package com.premisave.listing.controller;

import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.PaymentMethod;
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

    /**
     * Subscribe to a plan.
     *
     * POST /subscriptions?plan=BASIC&method=MPESA
     *
     * Requires authentication. HOME_OWNER accounts subscribe to unlock
     * the ability to promote listings.
     *
     * @param plan   the subscription tier (BASIC, PREMIUM, ULTIMATE)
     * @param method payment method — defaults to MPESA (primary Kenyan method)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Subscription> createSubscription(
            @RequestParam SubscriptionPlan plan,
            @RequestParam(defaultValue = "MPESA") PaymentMethod method,
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        Subscription subscription = subscriptionService.createSubscription(ownerId, plan, method);
        return ResponseEntity.ok(subscription);
    }

    /**
     * Get all subscriptions (history) for the current user.
     *
     * GET /subscriptions/me
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Subscription>> getMySubscriptions(
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(subscriptionService.getUserSubscriptions(ownerId));
    }

    /**
     * Get the current active subscription, if any.
     *
     * GET /subscriptions/active
     */
    @GetMapping("/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Subscription> getActiveSubscription(
            @RequestHeader("Authorization") String authorization) {

        String ownerId = jwtUtil.extractUserId(authorization);
        return ResponseEntity.ok(subscriptionService.getActiveSubscription(ownerId));
    }

    /**
     * Cancel (deactivate) a specific subscription.
     * Admin or the subscription owner should call this.
     *
     * DELETE /subscriptions/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> cancelSubscription(
            @PathVariable String id,
            @RequestHeader("Authorization") String authorization) {

        subscriptionService.deactivateSubscription(id);
        return ResponseEntity.ok("Subscription cancelled successfully.");
    }
}