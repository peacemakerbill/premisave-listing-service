package com.premisave.listing.repository;

import com.premisave.listing.entity.ListingPromotion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListingPromotionRepository extends MongoRepository<ListingPromotion, String> {

    List<ListingPromotion> findByOwnerId(String ownerId);
    List<ListingPromotion> findByListingId(String listingId);
    
    Optional<ListingPromotion> findFirstByListingIdAndEndDateAfterOrderByEndDateDesc(
            String listingId, LocalDateTime now);
}