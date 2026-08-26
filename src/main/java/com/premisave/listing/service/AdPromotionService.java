package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.AdPromotionRequest;
import com.premisave.listing.dto.AdPromotionResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingPromotion;
import com.premisave.listing.entity.Payment;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.enums.PaymentStatus;
import com.premisave.listing.exception.AuthenticationFailedException;
import com.premisave.listing.exception.WalletServiceUnavailableException;
import com.premisave.listing.repository.ListingPromotionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Reused from property-service's existing convention (see the
     *  premisave-backend Postman collection) rather than inventing a new
     *  wallet-service "service" tag — confirm with whoever owns
     *  wallet-service whether listing promotions and property ad
     *  subscriptions are meant to share this tag. */
    private static final String WALLET_SERVICE_TAG = "AD_SUBSCRIPTION";

    private final ListingPromotionRepository promotionRepository;
    private final PaymentService paymentService;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;

    @Value("${ad.promotion.daily-rate:299}")
    private BigDecimal dailyRate;

    @Value("${ad.promotion.default-currency:KES}")
    private String defaultCurrency;

    // ====================== PROMOTE ======================

    /**
     * Promotes a listing for a given number of days, paid for via a
     * wallet-service debit.
     *
     * Unlike the old M-Pesa flow, this is synchronous end-to-end: the debit
     * either succeeds (and the listing is activated in this same call) or
     * fails (and nothing is activated) — there is no more PENDING-then-
     * wait-for-callback state, since debiting an existing wallet balance
     * isn't an async gateway operation the way funding one via M-Pesa was.
     * The `paymentMethod` parameter this used to take is gone entirely —
     * wallet-service abstracts the funding method away completely now.
     *
     * @param request    promotion request (listingId, days, optional custom rate)
     * @param userId     authenticated user's ID (from JWT)
     * @param authHeader full Authorization header for the identity check via auth-service
     * @return AdPromotionResponse with promotion details
     */
    @Transactional
    public AdPromotionResponse promoteListing(AdPromotionRequest request,
                                              String userId,
                                              String authHeader) {
        // 1. Verify identity
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
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

        // 4. Resolve effective daily rate: customDailyRate overrides base rate
        int days = request.getDays();
        BigDecimal effectiveRate = resolveEffectiveRate(request.getCustomDailyRate());
        BigDecimal totalAmount = effectiveRate.multiply(BigDecimal.valueOf(days));

        // 5. Create the promotion row first (PENDING) so its id is available
        //    to use as the wallet-service reference.
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
        promotion.setPaymentStatus(PaymentStatus.PENDING);
        promotion = promotionRepository.save(promotion);

        // 6. Debit the wallet via wallet-service
        Payment payment;
        try {
            payment = paymentService.processPayment(
                    userId,
                    promotion.getId(),
                    totalAmount,
                    WALLET_SERVICE_TAG,
                    "Listing promotion: " + days + " day(s) for listing " + listing.getId()
            );
        } catch (WalletServiceUnavailableException e) {
            // wallet-service itself is unreachable — mark this attempt
            // FAILED so the promotion row doesn't sit as PENDING forever,
            // then propagate so the caller gets the correct "service
            // unavailable" message instead of one implying insufficient
            // funds.
            promotion.setPaymentStatus(PaymentStatus.FAILED);
            promotionRepository.save(promotion);
            throw e;
        }

        promotion.setPaymentId(payment.getId());
        promotion.setPaymentStatus(payment.getStatus());
        promotion = promotionRepository.save(promotion);

        // 7. Activate the listing immediately if the debit succeeded — no
        //    more waiting on an async callback.
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            listing.setPromoted(true);
            listing.setPromotionEndDate(promotion.getEndDate());
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setActive(true);
            listingService.saveListing(listing);

            log.info("Listing {} promoted for {} days by user {} at KES {}/day. Payment: {}, Expires: {}",
                    listing.getId(), days, userId, effectiveRate, payment.getId(), promotion.getEndDate());
        } else {
            log.warn("Listing {} promotion payment failed for user {}: paymentId={}",
                    listing.getId(), userId, payment.getId());
        }

        return new AdPromotionResponse(
                promotion.getId(),
                listing.getId(),
                days,
                totalAmount,
                promotion.getEndDate(),
                payment.getStatus() == PaymentStatus.COMPLETED
                        ? "Promotion activated successfully! Your listing is now live."
                        : "Payment failed — your wallet may have insufficient funds. Please top up and try again.",
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
     * 4. The wallet debit is processed before the listing is updated, and
     *    (as above) resolves synchronously — no PaymentMethod parameter
     *    anymore.
     */
    @Transactional
    public AdPromotionResponse extendPromotion(String listingId,
                                               int additionalDays,
                                               String userId,
                                               String authHeader) {
        UserSummaryResponse user = authServiceClient.getCurrentUser(authHeader);
        if (user == null || !user.getId().equals(userId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        if (additionalDays < 1) {
            throw new RuntimeException("You must extend by at least 1 day.");
        }

        Listing listing = (Listing) listingService.getListingById(listingId);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only extend promotion on your own listings.");
        }

        BigDecimal effectiveRate = resolveEffectiveRate(null);
        BigDecimal totalAmount = effectiveRate.multiply(BigDecimal.valueOf(additionalDays));

        LocalDateTime baseDate = (listing.isPromoted()
                && listing.getPromotionEndDate() != null
                && listing.getPromotionEndDate().isAfter(LocalDateTime.now()))
                ? listing.getPromotionEndDate()
                : LocalDateTime.now();
        LocalDateTime newEndDate = baseDate.plusDays(additionalDays);

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
        extension.setPaymentStatus(PaymentStatus.PENDING);
        extension = promotionRepository.save(extension);

        Payment payment;
        try {
            payment = paymentService.processPayment(
                    userId,
                    extension.getId(),
                    totalAmount,
                    WALLET_SERVICE_TAG,
                    "Listing promotion extension: +" + additionalDays + " day(s) for listing " + listingId
            );
        } catch (WalletServiceUnavailableException e) {
            extension.setPaymentStatus(PaymentStatus.FAILED);
            promotionRepository.save(extension);
            throw e;
        }

        extension.setPaymentId(payment.getId());
        extension.setPaymentStatus(payment.getStatus());
        extension = promotionRepository.save(extension);

        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            listing.setPromoted(true);
            listing.setPromotionEndDate(newEndDate);
            listing.setStatus(ListingStatus.ACTIVE);
            listing.setActive(true);
            listingService.saveListing(listing);

            log.info("Promotion extended: listing={}, +{}days, newExpiry={}, user={}, rate=KES{}, payment={}",
                    listingId, additionalDays, newEndDate, userId, effectiveRate, payment.getId());
        } else {
            log.warn("Promotion extension payment failed: listing={}, user={}, paymentId={}",
                    listingId, userId, payment.getId());
        }

        return new AdPromotionResponse(
                extension.getId(),
                listingId,
                additionalDays,
                totalAmount,
                newEndDate,
                payment.getStatus() == PaymentStatus.COMPLETED
                        ? "Promotion extended to " + newEndDate.toLocalDate() + "."
                        : "Payment failed — your wallet may have insufficient funds. Please top up and try again.",
                payment.getStatus() == PaymentStatus.COMPLETED
        );
    }

    // ====================== SCHEDULED: DEACTIVATE EXPIRED PROMOTIONS ======================

    /**
     * Runs every hour. Only queries promotions whose end date has passed —
     * avoids loading the entire promotions collection. Now backed by a
     * compound index on (endDate, paymentStatus) — see ListingPromotion.
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

    /** Paginated — previously returned every promotion a user ever had in
     *  one unbounded list. */
    public Page<ListingPromotion> getUserPromotions(String ownerId, Pageable pageable) {
        return promotionRepository.findByOwnerId(ownerId, pageable);
    }

    // ====================== PRIVATE HELPERS ======================

    /**
     * Resolves the effective daily rate for a promotion or extension.
     *
     * Priority:
     * 1. customDailyRate from request (if positive) — always wins.
     * 2. Base daily rate from config.
     *
     * @param customDailyRate optional override from the request DTO
     * @return the resolved rate
     */
    private BigDecimal resolveEffectiveRate(BigDecimal customDailyRate) {
        if (customDailyRate != null && customDailyRate.compareTo(BigDecimal.ZERO) > 0) {
            return customDailyRate;
        }
        return dailyRate;
    }
}