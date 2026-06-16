package com.premisave.listing.repository;

import com.premisave.listing.entity.HouseSale;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface HouseSaleRepository extends MongoRepository<HouseSale, String> {
    List<HouseSale> findByOwnerId(String ownerId);
}