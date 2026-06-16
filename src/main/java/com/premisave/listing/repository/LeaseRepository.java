package com.premisave.listing.repository;

import com.premisave.listing.entity.Lease;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface LeaseRepository extends MongoRepository<Lease, String> {
    List<Lease> findByOwnerId(String ownerId);
}