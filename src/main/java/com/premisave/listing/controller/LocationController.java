package com.premisave.listing.controller;

import com.premisave.listing.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/nearby")
    public ResponseEntity<List<?>> findNearbyListings(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "10") Double radiusKm) {
        
        return ResponseEntity.ok(locationService.findNearbyListings(latitude, longitude, radiusKm));
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchByLocation(@RequestParam String city) {
        return ResponseEntity.ok(locationService.searchByCity(city));
    }
}