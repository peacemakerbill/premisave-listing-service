package com.premisave.listing.repository;

import com.premisave.listing.entity.ShortTermRental;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ShortTermRentalRepository extends MongoRepository<ShortTermRental, String> {
    List<ShortTermRental> findByOwnerId(String ownerId);
    List<ShortTermRental> findByCityAndActiveTrue(String city);
}