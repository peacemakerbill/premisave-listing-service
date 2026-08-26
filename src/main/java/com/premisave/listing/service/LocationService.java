package com.premisave.listing.service;

import com.premisave.listing.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationService {

    private final MongoTemplate mongoTemplate;

    /**
     * Find listings near a specific location using bounding box approximation.
     *
     * NOTE: this remains a rectangular approximation (not a true radius) and
     * does not use a geospatial index. At real data volumes this should
     * become a GeoJSON Point field with a 2dsphere index and a
     * $nearSphere/$geoWithin query — but that change touches how listings
     * are created/updated (ListingRequest, ListingService), which weren't
     * available when this pass was done, so it's left structurally as-is
     * for now rather than guessed at.
     */
    public List<Object> findNearbyListings(Double latitude, Double longitude, Double radiusKm) {
        if (latitude == null || longitude == null || radiusKm == null || radiusKm <= 0) {
            return List.of();
        }

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
     * Search listings by city.
     *
     * Previously, short-term rentals used an exact match
     * (findByCityAndActiveTrue) while every other category used a
     * case-insensitive substring match via findAll().stream().filter(...) —
     * so the SAME search term behaved differently depending on which
     * listing type it happened to match, and four of the five categories
     * pulled their entire collection into application memory on every
     * call. This now runs one consistent query (case-insensitive substring,
     * via Mongo's own regex operator) across all five collections at the
     * database level instead of in the JVM.
     *
     * NOTE: a substring ("contains") match can't use a plain index the way
     * a prefix match can, so this specific query still isn't index-backed —
     * real text/city search at high volume would want a MongoDB text index
     * or a dedicated search service. This fixes the correctness bug and the
     * in-memory full-scan; it isn't a full search engine.
     */
    public List<Object> searchByCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return List.of();
        }

        String pattern = Pattern.quote(city.trim());

        Query query = new Query();
        query.addCriteria(Criteria.where("city").regex(pattern, "i"));
        query.addCriteria(Criteria.where("active").is(true));

        List<Object> results = new ArrayList<>();
        results.addAll(mongoTemplate.find(query, ShortTermRental.class));
        results.addAll(mongoTemplate.find(query, LongTermRental.class));
        results.addAll(mongoTemplate.find(query, HouseSale.class));
        results.addAll(mongoTemplate.find(query, LandSale.class));
        results.addAll(mongoTemplate.find(query, Lease.class));

        log.info("Found {} listings in city: {}", results.size(), city);
        return results;
    }

    /**
     * Find listings within map bounds (for frontend map view)
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