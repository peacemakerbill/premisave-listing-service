package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingPromotion;
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

    @Value("${ad.promotion.default-currency:USD}")
    private String defaultCurrency;

    @Transactional
    public AdPromotionResponse promoteListing(AdPromotionRequest request, String userId, String authHeader) {
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new RuntimeException("User not authenticated");
        }

        Listing listing = (Listing) listingService.getListingById(request.getListingId());
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only promote your own listings");
        }

        int days = request.getDays();
        BigDecimal rate = request.getCustomDailyRate() != null ? request.getCustomDailyRate() : dailyRate;
        BigDecimal totalAmount = rate.multiply(BigDecimal.valueOf(days));

        ListingPromotion promotion = new ListingPromotion();
        promotion.setListingId(listing.getId());
        promotion.setOwnerId(userId);
        promotion.setDays(days);
        promotion.setDailyRate(rate);
        promotion.setTotalAmount(totalAmount);
        promotion.setCurrency(defaultCurrency);
        promotion.setStartDate(LocalDateTime.now());
        promotion.setEndDate(LocalDateTime.now().plusDays(days));
        promotion.setPaymentStatus(PaymentStatus.COMPLETED);

        paymentService.processPayment(userId, null, totalAmount, PaymentMethod.PAYPAL);

        ListingPromotion savedPromotion = promotionRepository.save(promotion);

        listing.setPromoted(true);
        listing.setPromotionEndDate(savedPromotion.getEndDate());
        listing.setStatus(ListingStatus.ACTIVE);
        listingService.saveListing(listing);

        log.info("Listing {} promoted for {} days. Expires: {}", listing.getId(), days, savedPromotion.getEndDate());

        return new AdPromotionResponse(
                savedPromotion.getId(),
                listing.getId(),
                days,
                totalAmount,
                savedPromotion.getEndDate(),
                "Promotion activated successfully",
                true
        );
    }

    @Transactional
    public AdPromotionResponse extendPromotion(String listingId, int additionalDays, String userId, String authHeader) {
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new RuntimeException("User not authenticated");
        }

        Listing listing = (Listing) listingService.getListingById(listingId);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only extend your own listings");
        }

        BigDecimal totalAmount = dailyRate.multiply(BigDecimal.valueOf(additionalDays));

        ListingPromotion promotion = new ListingPromotion();
        promotion.setListingId(listingId);
        promotion.setOwnerId(userId);
        promotion.setDays(additionalDays);
        promotion.setDailyRate(dailyRate);
        promotion.setTotalAmount(totalAmount);
        promotion.setCurrency(defaultCurrency);
        promotion.setStartDate(LocalDateTime.now());
        promotion.setEndDate(LocalDateTime.now().plusDays(additionalDays));
        promotion.setPaymentStatus(PaymentStatus.COMPLETED);

        paymentService.processPayment(userId, null, totalAmount, PaymentMethod.PAYPAL);
        promotionRepository.save(promotion);

        // Calculate new end date
        LocalDateTime newEndDate = (listing.getPromotionEndDate() != null && listing.getPromotionEndDate().isAfter(LocalDateTime.now()))
                ? listing.getPromotionEndDate().plusDays(additionalDays)
                : LocalDateTime.now().plusDays(additionalDays);

        listing.setPromoted(true);
        listing.setPromotionEndDate(newEndDate);
        listing.setStatus(ListingStatus.ACTIVE);
        listingService.saveListing(listing);

        log.info("Extended promotion for listing {} by {} days. New expiry: {}", listingId, additionalDays, newEndDate);

        return new AdPromotionResponse(
                promotion.getId(),
                listingId,
                additionalDays,
                totalAmount,
                newEndDate,
                "Promotion extended successfully",
                true
        );
    }

    /** Scheduled Job - Deactivate Expired Promotions (runs every hour) */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deactivateExpiredPromotions() {
        log.info("Starting scheduled task: Checking for expired promotions...");

        List<ListingPromotion> promotions = promotionRepository.findAll();
        int deactivatedCount = 0;

        for (ListingPromotion promo : promotions) {
            if (promo.getEndDate() != null && promo.getEndDate().isBefore(LocalDateTime.now())) {
                try {
                    Listing listing = (Listing) listingService.getListingById(promo.getListingId());
                    if (listing != null && listing.isPromoted()) {
                        listing.setPromoted(false);
                        listing.setPromotionEndDate(null);
                        listingService.saveListing(listing);
                        deactivatedCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to deactivate promotion for listing {}: {}", promo.getListingId(), e.getMessage());
                }
            }
        }

        if (deactivatedCount > 0) {
            log.info("Successfully deactivated {} expired promotions", deactivatedCount);
        }
    }

    public List<ListingPromotion> getUserPromotions(String ownerId) {
        return promotionRepository.findByOwnerId(ownerId);
    }
}