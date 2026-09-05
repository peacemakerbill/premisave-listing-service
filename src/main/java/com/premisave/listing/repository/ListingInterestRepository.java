package com.premisave.listing.repository;

import com.premisave.listing.entity.ListingInterest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListingInterestRepository extends MongoRepository<ListingInterest, String> {

    Page<ListingInterest> findByCustomerId(String customerId, Pageable pageable);

    Page<ListingInterest> findByListingOwnerId(String listingOwnerId, Pageable pageable);

    Page<ListingInterest> findByListingId(String listingId, Pageable pageable);

    Optional<ListingInterest> findByListingIdAndCustomerId(String listingId, String customerId);
}