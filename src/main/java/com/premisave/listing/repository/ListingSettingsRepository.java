package com.premisave.listing.repository;

import com.premisave.listing.entity.ListingSettings;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ListingSettingsRepository extends MongoRepository<ListingSettings, String> {
}
