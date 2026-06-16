package com.premisave.listing.repository;

import com.premisave.listing.entity.LongTermRental;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LongTermRentalRepository extends MongoRepository<LongTermRental, String> {
    List<LongTermRental> findByOwnerId(String ownerId);
}