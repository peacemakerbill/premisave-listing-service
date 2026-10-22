package com.premisave.listing.repository;

import com.premisave.listing.entity.ImageAsset;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ImageAssetRepository extends MongoRepository<ImageAsset, String> {
}
