package com.premisave.listing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LocationService {

    public List<Map<String, Object>> findNearbyListings(Double lat, Double lng, Double radiusKm) {
        // Implement geo query here
        return List.of();
    }

    public List<?> searchByCity(String city) {
        // Implement city-based search
        return List.of();
    }
}