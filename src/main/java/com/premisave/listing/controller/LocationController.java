package com.premisave.listing.controller;

import com.premisave.listing.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    /**
     * Find listings near a specific location (Great for map view)
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<?>> findNearbyListings(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "10.0") Double radiusKm) {
        
        List<?> listings = locationService.findNearbyListings(latitude, longitude, radiusKm);
        return ResponseEntity.ok(listings);
    }

    /**
     * Search listings by city
     */
    @GetMapping("/city")
    public ResponseEntity<List<?>> searchByCity(@RequestParam String city) {
        List<?> listings = locationService.searchByCity(city);
        return ResponseEntity.ok(listings);
    }

    /**
     * Search listings within map bounds (for frontend map view)
     */
    @GetMapping("/bounds")
    public ResponseEntity<List<?>> findInBounds(
            @RequestParam Double minLat,
            @RequestParam Double maxLat,
            @RequestParam Double minLng,
            @RequestParam Double maxLng) {
        
        List<?> listings = locationService.findListingsInBounds(minLat, maxLat, minLng, maxLng);
        return ResponseEntity.ok(listings);
    }
}