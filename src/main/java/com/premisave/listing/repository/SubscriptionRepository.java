package com.premisave.listing.repository;

import com.premisave.listing.entity.Subscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends MongoRepository<Subscription, String> {

    List<Subscription> findByOwnerId(String ownerId);

    /**
     * Find an active subscription for an owner.
     * Uses the subscriptionActive field (NOT BaseEntity.active).
     */
    Optional<Subscription> findByOwnerIdAndSubscriptionActiveTrue(String ownerId);

    /**
     * Used by the scheduled job to find all active subscriptions for expiry checks.
     */
    List<Subscription> findAllBySubscriptionActiveTrue();
}