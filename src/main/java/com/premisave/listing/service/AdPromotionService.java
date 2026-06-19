package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingPromotion;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.enums.PaymentMethod;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.repository.ListingPromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdPromotionService {

    private final ListingPromotionRepository promotionRepository;
    private final PaymentService paymentService;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;

    @Value("${ad.promotion.daily-rate:2.99}")
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
     *
     * @param request   promotion request (listingId, days, optional custom rate)
     * @param userId    authenticated user's ID (from JWT)
     * @param authHeader full Authorization header for downstream auth service calls
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

        // 4. Calculate cost
        int days = request.getDays();
        BigDecimal rate = (request.getCustomDailyRate() != null
                && request.getCustomDailyRate().compareTo(BigDecimal.ZERO) > 0)
                ? request.getCustomDailyRate()
                : dailyRate;
        BigDecimal totalAmount = rate.multiply(BigDecimal.valueOf(days));

        // 5. Process payment FIRST — if this throws, nothing else is persisted
        Payment payment = paymentService.processPayment(
                userId,
                null,           // not linked to a subscription
                totalAmount,
                paymentMethod
        );

        // 6. Build and save promotion record
        LocalDateTime now = LocalDateTime.now();
        ListingPromotion promotion = new ListingPromotion();
        promotion.setListingId(listing.getId());
        promotion.setOwnerId(userId);
        promotion.setDays(days);
        promotion.setDailyRate(rate);
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

        log.info("Listing {} promoted for {} days by user {}. Payment: {}, Expires: {}",
                listing.getId(), days, userId, payment.getId(), savedPromotion.getEndDate());

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

        // 3. Calculate cost
        BigDecimal totalAmount = dailyRate.multiply(BigDecimal.valueOf(additionalDays));

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
        extension.setDailyRate(dailyRate);
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

        log.info("Promotion extended: listing={}, +{}days, newExpiry={}, user={}, payment={}",
                listingId, additionalDays, newEndDate, userId, payment.getId());

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

        // Efficient query — only fetch promotions that have ended
        List<ListingPromotion> expired = promotionRepository.findByEndDateBeforeAndPaymentStatus(
                LocalDateTime.now(), PaymentStatus.COMPLETED);

        int count = 0;
        for (ListingPromotion promo : expired) {
            try {
                Listing listing = (Listing) listingService.getListingById(promo.getListingId());
                if (listing != null && listing.isPromoted()) {
                    // Check no newer promotion is active for the same listing
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
}