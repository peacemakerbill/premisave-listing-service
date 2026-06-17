package com.premisave.listing.service;

import com.premisave.listing.entity.HouseSale;
import com.premisave.listing.entity.LandSale;
import com.premisave.listing.entity.Lease;
import com.premisave.listing.entity.LongTermRental;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final MongoTemplate mongoTemplate;
    private final ShortTermRentalRepository shortTermRentalRepository;
    private final LongTermRentalRepository longTermRentalRepository;
    private final HouseSaleRepository houseSaleRepository;
    private final LandSaleRepository landSaleRepository;
    private final LeaseRepository leaseRepository;

    /**
     * Find listings near a specific location (using bounding box approximation)
     */
    public List<Object> findNearbyListings(Double latitude, Double longitude, Double radiusKm) {
        if (latitude == null || longitude == null || radiusKm == null || radiusKm <= 0) {
            return List.of();
        }

        // Approximate bounding box (1 degree ≈ 111 km)
        double latDelta = radiusKm / 111.0;
        double lngDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(latitude)));

        Query query = new Query();
        query.addCriteria(Criteria.where("latitude").gte(latitude - latDelta).lte(latitude + latDelta));
        query.addCriteria(Criteria.where("longitude").gte(longitude - lngDelta).lte(longitude + lngDelta));
        query.addCriteria(Criteria.where("active").is(true));

        query.with(Sort.by(Sort.Direction.ASC, "price"));

        List<Object> results = new ArrayList<>();
        results.addAll(mongoTemplate.find(query, ShortTermRental.class));
        results.addAll(mongoTemplate.find(query, LongTermRental.class));
        results.addAll(mongoTemplate.find(query, HouseSale.class));
        results.addAll(mongoTemplate.find(query, LandSale.class));
        results.addAll(mongoTemplate.find(query, Lease.class));

        log.info("Found {} listings near lat:{}, lng:{}, radius:{}km", 
                results.size(), latitude, longitude, radiusKm);

        return results;
    }

    /**
     * Search listings by city
     */
    public List<Object> searchByCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return List.of();
        }

        String cityLower = city.trim().toLowerCase();

        List<Object> results = new ArrayList<>();
        
        // Short term rentals have dedicated query method
        results.addAll(shortTermRentalRepository.findByCityAndActiveTrue(city));

        // Other types - manual filter
        results.addAll(longTermRentalRepository.findAll().stream()
                .filter(l -> l.getCity() != null && l.getCity().toLowerCase().contains(cityLower))
                .toList());
        results.addAll(houseSaleRepository.findAll().stream()
                .filter(l -> l.getCity() != null && l.getCity().toLowerCase().contains(cityLower))
                .toList());
        results.addAll(landSaleRepository.findAll().stream()
                .filter(l -> l.getCity() != null && l.getCity().toLowerCase().contains(cityLower))
                .toList());
        results.addAll(leaseRepository.findAll().stream()
                .filter(l -> l.getCity() != null && l.getCity().toLowerCase().contains(cityLower))
                .toList());

        log.info("Found {} listings in city: {}", results.size(), city);
        return results;
    }

    /**
     * Find listings within map bounds (for frontend map)
     */
    public List<Object> findListingsInBounds(Double minLat, Double maxLat, 
                                           Double minLng, Double maxLng) {
        if (minLat == null || maxLat == null || minLng == null || maxLng == null) {
            return List.of();
        }

        Query query = new Query();
        query.addCriteria(Criteria.where("latitude").gte(minLat).lte(maxLat));
        query.addCriteria(Criteria.where("longitude").gte(minLng).lte(maxLng));
        query.addCriteria(Criteria.where("active").is(true));

        List<Object> results = new ArrayList<>();
        results.addAll(mongoTemplate.find(query, ShortTermRental.class));
        results.addAll(mongoTemplate.find(query, LongTermRental.class));
        results.addAll(mongoTemplate.find(query, HouseSale.class));
        results.addAll(mongoTemplate.find(query, LandSale.class));
        results.addAll(mongoTemplate.find(query, Lease.class));

        log.info("Found {} listings in bounds: lat[{}-{}], lng[{}-{}]", 
                results.size(), minLat, maxLat, minLng, maxLng);

        return results;
    }
}