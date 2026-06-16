package com.premisave.listing.repository;

import com.premisave.listing.entity.Subscription;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends MongoRepository<Subscription, String> {
    List<Subscription> findByOwnerId(String ownerId);
    Optional<Subscription> findByOwnerIdAndActiveTrue(String ownerId);
}