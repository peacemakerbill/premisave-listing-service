package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingPromotion;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.entity.Subscription;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.enums.SubscriptionPlan;
import com.premisave.listing.repository.ListingPromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdPromotionService {

    private final ListingPromotionRepository promotionRepository;
    private final PaymentService paymentService;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;
    private final SubscriptionService subscriptionService;

    @Value("${ad.promotion.daily-rate:299}")
    private BigDecimal dailyRate;

    @Value("${ad.promotion.default-currency:KES}")
    private String defaultCurrency;

    // ====================== PROMOTE ======================

    /**
     * Promotes a listing for a given number of days.
     *
     * Rules:
     * 1. Only the listing owner can promote their own listing.
     * 2. A listing that is already actively promoted cannot be promoted again
     *    — the owner must use extendPromotion instead.
     * 3. Payment is processed before the promotion record is written.
     *    If payment fails, no promotion is created (transactional rollback).
     * 4. After payment succeeds, the listing is set ACTIVE and visible.
     * 5. Subscribers receive a discounted daily rate based on their plan tier.
     *    customDailyRate from the request always overrides everything.
     *
     * @param request       promotion request (listingId, days, optional custom rate)
     * @param userId        authenticated user's ID (from JWT)
     * @param authHeader    full Authorization header for downstream auth service calls
     * @param paymentMethod chosen payment method
     * @return AdPromotionResponse with promotion details
     */
    @Transactional
    public AdPromotionResponse promoteListing(AdPromotionRequest request,
                                              String userId,
                                              String authHeader,
                                              PaymentMethod paymentMethod) {
        // 1. Verify identity
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new RuntimeException("User authentication failed. Please log in again.");
        }

        // 2. Fetch listing and verify ownership
        Listing listing = (Listing) listingService.getListingById(request.getListingId());
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only promote your own listings.");
        }

        // 3. Guard: prevent promoting an already-active promotion
        if (listing.isPromoted()
                && listing.getPromotionEndDate() != null
                && listing.getPromotionEndDate().isAfter(LocalDateTime.now())) {
            long daysLeft = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDateTime.now(), listing.getPromotionEndDate());
            throw new RuntimeException(
                "This listing is already promoted. Your current promotion expires on " +
                listing.getPromotionEndDate().toLocalDate() +
                " (" + daysLeft + " day(s) remaining). " +
                "Use the 'extend promotion' option to add more days."
            );
        }

        // 4. Resolve effective daily rate:
        //    customDailyRate > subscription discount > base rate
        int days = request.getDays();
        BigDecimal effectiveRate = resolveEffectiveRate(request.getCustomDailyRate(), userId);
        BigDecimal totalAmount = effectiveRate.multiply(BigDecimal.valueOf(days));

        // 5. Process payment FIRST — if this throws, nothing else is persisted
        Payment payment = paymentService.processPayment(
                userId,
                null,
                totalAmount,
                paymentMethod
        );

        // 6. Build and save promotion record
        LocalDateTime now = LocalDateTime.now();
        ListingPromotion promotion = new ListingPromotion();
        promotion.setListingId(listing.getId());
        promotion.setOwnerId(userId);
        promotion.setDays(days);
        promotion.setDailyRate(effectiveRate);
        promotion.setTotalAmount(totalAmount);
        promotion.setCurrency(defaultCurrency);
        promotion.setStartDate(now);
        promotion.setEndDate(now.plusDays(days));
        promotion.setPaymentId(payment.getId());
        // Status mirrors the payment — PENDING for M-Pesa (confirmed asynchronously),
        // COMPLETED for synchronous methods.
        promotion.setPaymentStatus(payment.getStatus());

        ListingPromotion savedPromotion = promotionRepository.save(promotion);

        // 7. Activate listing only when payment is already confirmed (non-M-Pesa)
        //    For M-Pesa, activation happens in the M-Pesa callback handler.
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            listing.setPromoted(true);
            listing.setPromotionEndDate(savedPromotion.getEndDate());
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setActive(true);
            listingService.saveListing(listing);
        }

        log.info("Listing {} promoted for {} days by user {} at KES {}/day. Payment: {}, Expires: {}",
                listing.getId(), days, userId, effectiveRate, payment.getId(), savedPromotion.getEndDate());

        return new AdPromotionResponse(
                savedPromotion.getId(),
                listing.getId(),
                days,
                totalAmount,
                savedPromotion.getEndDate(),
                payment.getStatus() == PaymentStatus.COMPLETED
                        ? "Promotion activated successfully! Your listing is now live."
                        : "Payment initiated via M-Pesa. Your listing will go live once payment is confirmed.",
                payment.getStatus() == PaymentStatus.COMPLETED
        );
    }

    // ====================== EXTEND ======================

    /**
     * Extends an existing promotion (active OR recently expired) by additional days.
     *
     * Rules:
     * 1. Only the listing owner can extend.
     * 2. If promotion is still active, the new days are added to the existing end date.
     * 3. If promotion has expired, it restarts from now.
     * 4. Payment is processed before the listing is updated.
     * 5. Subscribers receive the same plan-based discount as in promoteListing().
     */
    @Transactional
    public AdPromotionResponse extendPromotion(String listingId,
                                               int additionalDays,
                                               String userId,
                                               String authHeader,
                                               PaymentMethod paymentMethod) {
        // 1. Verify identity
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new RuntimeException("User authentication failed. Please log in again.");
        }

        if (additionalDays < 1) {
            throw new RuntimeException("You must extend by at least 1 day.");
        }

        // 2. Fetch listing and verify ownership
        Listing listing = (Listing) listingService.getListingById(listingId);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only extend promotion on your own listings.");
        }

        // 3. Resolve effective daily rate (no customDailyRate on extend — use subscription/base)
        BigDecimal effectiveRate = resolveEffectiveRate(null, userId);
        BigDecimal totalAmount = effectiveRate.multiply(BigDecimal.valueOf(additionalDays));

        // 4. Process payment FIRST
        Payment payment = paymentService.processPayment(
                userId,
                null,
                totalAmount,
                paymentMethod
        );

        // 5. Determine new end date:
        //    - If currently promoted and not yet expired → extend from existing end date
        //    - Otherwise → start fresh from now
        LocalDateTime baseDate = (listing.isPromoted()
                && listing.getPromotionEndDate() != null
                && listing.getPromotionEndDate().isAfter(LocalDateTime.now()))
                ? listing.getPromotionEndDate()
                : LocalDateTime.now();

        LocalDateTime newEndDate = baseDate.plusDays(additionalDays);

        // 6. Record extension as a new promotion entry (preserves history)
        LocalDateTime now = LocalDateTime.now();
        ListingPromotion extension = new ListingPromotion();
        extension.setListingId(listingId);
        extension.setOwnerId(userId);
        extension.setDays(additionalDays);
        extension.setDailyRate(effectiveRate);
        extension.setTotalAmount(totalAmount);
        extension.setCurrency(defaultCurrency);
        extension.setStartDate(now);
        extension.setEndDate(newEndDate);
        extension.setPaymentId(payment.getId());
        extension.setPaymentStatus(payment.getStatus());
        promotionRepository.save(extension);

        // 7. Update listing if payment confirmed
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            listing.setPromoted(true);
            listing.setPromotionEndDate(newEndDate);
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setActive(true);
            listingService.saveListing(listing);
        }

        log.info("Promotion extended: listing={}, +{}days, newExpiry={}, user={}, rate=KES{}, payment={}",
                listingId, additionalDays, newEndDate, userId, effectiveRate, payment.getId());

        return new AdPromotionResponse(
                extension.getId(),
                listingId,
                additionalDays,
                totalAmount,
                newEndDate,
                payment.getStatus() == PaymentStatus.COMPLETED
                        ? "Promotion extended to " + newEndDate.toLocalDate() + "."
                        : "Extension payment initiated via M-Pesa. Extension will apply once payment is confirmed.",
                payment.getStatus() == PaymentStatus.COMPLETED
        );
    }

    // ====================== M-PESA CALLBACK ACTIVATION ======================

    /**
     * Called by PaymentService after an M-Pesa callback confirms a promotion payment.
     * Activates the listing tied to the promotion whose paymentId matches.
     */
    @Transactional
    public void activatePromotionAfterMpesaPayment(String paymentId) {
        List<ListingPromotion> promotions = promotionRepository.findByPaymentId(paymentId);
        if (promotions.isEmpty()) {
            log.warn("No promotion found for paymentId={}. Listing not activated.", paymentId);
            return;
        }

        ListingPromotion promo = promotions.get(0);
        promo.setPaymentStatus(PaymentStatus.COMPLETED);
        promotionRepository.save(promo);

        try {
            Listing listing = (Listing) listingService.getListingById(promo.getListingId());
            listing.setPromoted(true);
            listing.setPromotionEndDate(promo.getEndDate());
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setActive(true);
            listingService.saveListing(listing);
            log.info("Listing {} activated after M-Pesa payment confirmed: paymentId={}",
                    promo.getListingId(), paymentId);
        } catch (Exception e) {
            log.error("Failed to activate listing after M-Pesa callback for paymentId={}: {}",
                    paymentId, e.getMessage(), e);
        }
    }

    // ====================== SCHEDULED: DEACTIVATE EXPIRED PROMOTIONS ======================

    /**
     * Runs every hour. Only queries promotions whose end date has passed —
     * avoids loading the entire promotions collection.
     */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredPromotions() {
        log.info("Scheduled task: checking for expired promotions...");

        List<ListingPromotion> expired = promotionRepository.findByEndDateBeforeAndPaymentStatus(
                LocalDateTime.now(), PaymentStatus.COMPLETED);

        int count = 0;
        for (ListingPromotion promo : expired) {
            try {
                Listing listing = (Listing) listingService.getListingById(promo.getListingId());
                if (listing != null && listing.isPromoted()) {
                    boolean hasNewerPromotion = promotionRepository
                            .findByListingIdAndEndDateAfterAndPaymentStatus(
                                    listing.getId(), LocalDateTime.now(), PaymentStatus.COMPLETED)
                            .stream()
                            .anyMatch(p -> !p.getId().equals(promo.getId()));

                    if (!hasNewerPromotion) {
                        listing.setPromoted(false);
                        listing.setPromotionEndDate(null);
                        listing.setActive(false);
                        listing.setStatus(ListingStatus.PENDING);
                        listingService.saveListing(listing);
                        count++;
                        log.info("Listing {} promotion expired and deactivated.", listing.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error deactivating expired promotion for listingId={}: {}",
                        promo.getListingId(), e.getMessage());
            }
        }

        if (count > 0) {
            log.info("Deactivated {} expired listing promotions.", count);
        }
    }

    // ====================== QUERIES ======================

    public List<ListingPromotion> getUserPromotions(String ownerId) {
        return promotionRepository.findByOwnerId(ownerId);
    }

    // ====================== PRIVATE HELPERS ======================

    /**
     * Resolves the effective daily rate for a promotion or extension.
     *
     * Priority:
     * 1. customDailyRate from request (if positive) — always wins.
     * 2. Subscription-discounted rate (silently fetched; no exception if missing).
     * 3. Base daily rate from config.
     *
     * @param customDailyRate optional override from the request DTO
     * @param userId          owner's user ID used to look up their active subscription
     * @return the resolved rate, scaled to 2 decimal places
     */
    private BigDecimal resolveEffectiveRate(BigDecimal customDailyRate, String userId) {
        if (customDailyRate != null && customDailyRate.compareTo(BigDecimal.ZERO) > 0) {
            return customDailyRate;
        }

        // Silently attempt to fetch the active subscription — never throw to the caller
        try {
            Optional<Subscription> subscription = subscriptionService.getActiveSubscription(userId);
            if (subscription.isPresent()) {
                BigDecimal discounted = getDiscountedRate(subscription.get().getPlan());
                log.info("Subscription discount applied for user {}: plan={}, rate=KES{}",
                        userId, subscription.get().getPlan(), discounted);
                return discounted;
            }
        } catch (Exception e) {
            log.debug("No active subscription for user {} — using base rate. Reason: {}", userId, e.getMessage());
        }

        return dailyRate;
    }

    /**
     * Returns the discounted daily promotion rate for each subscription plan tier.
     *
     * Discount tiers:
     *   BASIC    → 10% off base rate
     *   PREMIUM  → 20% off base rate
     *   ULTIMATE → 35% off base rate
     *
     * @param plan the subscriber's active plan
     * @return discounted rate, rounded to 2 decimal places (HALF_UP)
     */
    private BigDecimal getDiscountedRate(SubscriptionPlan plan) {
        BigDecimal discountMultiplier = switch (plan) {
            case BASIC    -> BigDecimal.valueOf(0.90); // 10% off
            case PREMIUM  -> BigDecimal.valueOf(0.80); // 20% off
            case ULTIMATE -> BigDecimal.valueOf(0.65); // 35% off
        };
        return dailyRate.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
    }
}