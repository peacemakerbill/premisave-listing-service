package com.premisave.listing.service;

import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    public Subscription createSubscription(String ownerId, SubscriptionPlan plan) {
        Subscription sub = new Subscription();
        sub.setOwnerId(ownerId);
        sub.setPlan(plan);
        sub.setAmount(getPlanPrice(plan));
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(plan.getMonths()));
        sub.setActive(true);
        return subscriptionRepository.save(sub);
    }

    private BigDecimal getPlanPrice(SubscriptionPlan plan) {
        return switch (plan) {
            case BASIC -> BigDecimal.valueOf(29.99);
            case PREMIUM -> BigDecimal.valueOf(79.99);
            case ULTIMATE -> BigDecimal.valueOf(149.99);
        };
    }
}