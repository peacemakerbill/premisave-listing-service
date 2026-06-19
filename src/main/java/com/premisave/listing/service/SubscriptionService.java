package com.premisave.listing.service;

import com.premisave.listing.entity.Payment;
import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final PaymentService paymentService;

    /**
     * Creates a new subscription for the given owner.
     *
     * Rules enforced:
     * 1. Owner must not already have an active, non-expired subscription.
     * 2. Payment is processed immediately (M-Pesa STK push OR card).
     * 3. Subscription is only saved after payment succeeds.
     *
     * @param ownerId the authenticated home owner's user ID
     * @param plan    the chosen subscription plan
     * @param method  the payment method (MPESA, CARD, etc.)
     * @return the persisted Subscription
     */
    @Transactional
    public Subscription createSubscription(String ownerId, SubscriptionPlan plan, PaymentMethod method) {
        // 1. Block duplicate active subscriptions
        subscriptionRepository.findByOwnerIdAndSubscriptionActiveTrue(ownerId)
                .ifPresent(existing -> {
                    // Allow re-subscribe if the existing one has actually expired
                    if (existing.getEndDate() != null && existing.getEndDate().isAfter(LocalDateTime.now())) {
                        throw new RuntimeException(
                            "You already have an active " + existing.getPlan().name() +
                            " subscription that expires on " + existing.getEndDate().toLocalDate() +
                            ". You cannot create a new subscription until it expires."
                        );
                    }
                    // If it's marked active but already past end date, deactivate it now
                    existing.setSubscriptionActive(false);
                    subscriptionRepository.save(existing);
                });

        BigDecimal price = getPlanPrice(plan);

        // 2. Process payment first — if this throws, the subscription is NOT created
        Payment payment = paymentService.processPayment(ownerId, null, price, method);

        // 3. Build and persist subscription
        Subscription sub = new Subscription();
        sub.setOwnerId(ownerId);
        sub.setPlan(plan);
        sub.setAmount(price);
        sub.setStartDate(LocalDateTime.now());
        sub.setEndDate(LocalDateTime.now().plusMonths(plan.getMonths()));
        sub.setSubscriptionActive(true);
        sub.setPaymentId(payment.getId()); // Link payment to subscription

        Subscription saved = subscriptionRepository.save(sub);
        log.info("Subscription created: owner={}, plan={}, paymentId={}, expires={}",
                ownerId, plan, payment.getId(), saved.getEndDate());

        return saved;
    }

    /**
     * Returns all subscriptions (active and historical) for a user.
     */
    public List<Subscription> getUserSubscriptions(String ownerId) {
        return subscriptionRepository.findByOwnerId(ownerId);
    }

    /**
     * Returns the current active, non-expired subscription.
     * Throws if none found.
     */
    public Subscription getActiveSubscription(String ownerId) {
        return subscriptionRepository.findByOwnerIdAndSubscriptionActiveTrue(ownerId)
                .filter(sub -> sub.getEndDate() != null && sub.getEndDate().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new RuntimeException(
                    "No active subscription found for user: " + ownerId +
                    ". Please subscribe to a plan to create and manage listings."
                ));
    }

    /**
     * Manual cancellation of a subscription by its ID.
     * Only the owner or an admin should call this.
     */
    @Transactional
    public void deactivateSubscription(String subscriptionId) {
        Subscription sub = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new RuntimeException("Subscription not found: " + subscriptionId));

        if (!sub.isSubscriptionActive()) {
            throw new RuntimeException("Subscription is already inactive.");
        }

        sub.setSubscriptionActive(false);
        subscriptionRepository.save(sub);
        log.info("Subscription manually deactivated: {}", subscriptionId);
    }

    /**
     * Scheduled job that auto-deactivates expired subscriptions.
     * Runs every day at midnight.
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void deactivateExpiredSubscriptions() {
        log.info("Running scheduled job: deactivating expired subscriptions...");

        List<Subscription> active = subscriptionRepository.findAllBySubscriptionActiveTrue();
        int count = 0;

        for (Subscription sub : active) {
            if (sub.getEndDate() != null && sub.getEndDate().isBefore(LocalDateTime.now())) {
                sub.setSubscriptionActive(false);
                subscriptionRepository.save(sub);
                count++;
                log.info("Subscription expired and deactivated: id={}, owner={}", sub.getId(), sub.getOwnerId());
            }
        }

        if (count > 0) {
            log.info("Deactivated {} expired subscriptions.", count);
        }
    }

    // ====================== PLAN PRICING ======================

    public BigDecimal getPlanPrice(SubscriptionPlan plan) {
        return switch (plan) {
            case BASIC    -> BigDecimal.valueOf(29.99);
            case PREMIUM  -> BigDecimal.valueOf(79.99);
            case ULTIMATE -> BigDecimal.valueOf(149.99);
        };
    }
}