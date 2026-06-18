package com.premisave.listing.controller;

import com.premisave.listing.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system")
@RequiredArgsConstructor
public class SystemController {

    private final JwtUtil jwtUtil;

    // ====================== HEALTH CHECKS ======================
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "Premisave Listing Service");
        status.put("version", "0.0.1-SNAPSHOT");
        status.put("timestamp", LocalDateTime.now());
        status.put("environment", System.getProperty("spring.profiles.active", "default"));

        return ResponseEntity.ok(status);
    }

    @GetMapping("/health/details")
    public ResponseEntity<Map<String, Object>> healthDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("status", "UP");
        details.put("service", "Premisave Listing Service");
        details.put("port", 8082);
        details.put("javaVersion", System.getProperty("java.version"));
        details.put("database", "MongoDB");
        details.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(details);
    }

    // ====================== TOKEN TEST ENDPOINT ======================
    @GetMapping("/test-token")
    public ResponseEntity<Map<String, Object>> testToken(@RequestHeader("Authorization") String authorization) {
        String userId = jwtUtil.extractUserId(authorization);
        String username = jwtUtil.extractUsername(authorization);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "JWT Token is valid and working");
        response.put("userId", userId);
        response.put("username", username);
        response.put("timestamp", LocalDateTime.now());
        response.put("authorizationHeaderPresent", authorization != null);

        return ResponseEntity.ok(response);
    }
}