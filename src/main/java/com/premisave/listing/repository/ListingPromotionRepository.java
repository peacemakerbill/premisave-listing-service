package com.premisave.listing.repository;

import com.premisave.listing.entity.ListingPromotion;
import com.premisave.listing.enums.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ListingPromotionRepository extends MongoRepository<ListingPromotion, String> {

    List<ListingPromotion> findByOwnerId(String ownerId);

    List<ListingPromotion> findByListingId(String listingId);

    /**
     * Used by PaymentService to find the promotion linked to a confirmed payment.
     */
    List<ListingPromotion> findByPaymentId(String paymentId);

    /**
     * Efficient scheduler query — only expired promotions that were successfully paid.
     * Avoids loading the entire collection.
     */
    List<ListingPromotion> findByEndDateBeforeAndPaymentStatus(
            LocalDateTime endDate, PaymentStatus paymentStatus);

    /**
     * Used to check if a listing has a newer active promotion before deactivating.
     */
    List<ListingPromotion> findByListingIdAndEndDateAfterAndPaymentStatus(
            String listingId, LocalDateTime endDate, PaymentStatus paymentStatus);
}