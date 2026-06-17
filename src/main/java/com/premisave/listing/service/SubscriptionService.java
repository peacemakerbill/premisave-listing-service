package com.premisave.listing.service;

import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public Subscription createSubscription(String ownerId, SubscriptionPlan plan) {
        // Check if user already has active subscription
        subscriptionRepository.findByOwnerIdAndActiveTrue(ownerId)
                .ifPresent(existing -> {
                    throw new RuntimeException("User already has an active subscription");
                });

        Subscription sub = new Subscription();
        sub.setOwnerId(ownerId);
        sub.setPlan(plan);
        sub.setAmount(getPlanPrice(plan));
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(plan.getMonths()));
        sub.setActive(true);

        Subscription saved = subscriptionRepository.save(sub);
        log.info("New subscription created for owner: {} - Plan: {}", ownerId, plan);

        return saved;
    }

    private BigDecimal getPlanPrice(SubscriptionPlan plan) {
        return switch (plan) {
            case BASIC -> BigDecimal.valueOf(29.99);
            case PREMIUM -> BigDecimal.valueOf(79.99);
            case ULTIMATE -> BigDecimal.valueOf(149.99);
        };
    }

    public List<Subscription> getUserSubscriptions(String ownerId) {
        return subscriptionRepository.findByOwnerId(ownerId);
    }

    public Subscription getActiveSubscription(String ownerId) {
        return subscriptionRepository.findByOwnerIdAndActiveTrue(ownerId)
                .orElseThrow(() -> new RuntimeException("No active subscription found for user: " + ownerId));
    }

    @Transactional
    public void deactivateSubscription(String subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        sub.setActive(false);
        subscriptionRepository.save(sub);
        log.info("Subscription deactivated: {}", subscriptionId);
    }
}