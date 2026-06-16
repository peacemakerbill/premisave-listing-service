package com.premisave.listing.repository;

import com.premisave.listing.entity.LandSale;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LandSaleRepository extends MongoRepository<LandSale, String> {
    List<LandSale> findByOwnerId(String ownerId);
}