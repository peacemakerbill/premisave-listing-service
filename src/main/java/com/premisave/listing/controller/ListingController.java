package com.premisave.listing.controller;

import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.dto.auth_service.ProfileViewResponse;
import com.premisave.listing.dto.auth_service.ProfileViewStats;
import com.premisave.listing.dto.auth_service.SocialActionRequest;
import com.premisave.listing.dto.auth_service.SocialActionResponse;
import com.premisave.listing.dto.auth_service.WhoIViewedResponse;
import com.premisave.listing.entity.ShortTermRental;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.service.ListingService;
import com.premisave.listing.service.SocialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/listings")
@RequiredArgsConstructor
public class ListingController {

    private final ListingService listingService;
    private final SocialService socialService;

    // ====================== LISTING CRUD ======================
    @PostMapping
    public ResponseEntity<ListingResponse> createListing(
            @Valid @RequestBody ListingRequest request,
            @RequestHeader("Authorization") String authorization) {

        ListingResponse response = listingService.createListing(request, authorization);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/short-term")
    public ResponseEntity<List<ShortTermRental>> getShortTermRentals(
            @RequestParam(required = false) String city) {
        return ResponseEntity.ok(listingService.getShortTermRentals(city));
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<?> getListingsByOwner(
            @PathVariable String ownerId,
            @RequestParam ListingCategory category) {
        return ResponseEntity.ok(listingService.getListingsByOwner(ownerId, category));
    }

    // ====================== SOCIAL FEATURES ======================
    @PostMapping("/social/like")
    public ResponseEntity<SocialActionResponse> likeUser(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.likeUser(request, token));
    }

    @DeleteMapping("/social/unlike/{targetId}")
    public ResponseEntity<SocialActionResponse> unlikeUser(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unlikeUser(targetId, token));
    }

    @PostMapping("/social/follow")
    public ResponseEntity<SocialActionResponse> followUser(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.followUser(request, token));
    }

    @DeleteMapping("/social/unfollow/{targetId}")
    public ResponseEntity<SocialActionResponse> unfollowUser(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unfollowUser(targetId, token));
    }

    @PostMapping("/social/review")
    public ResponseEntity<SocialActionResponse> reviewUser(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.reviewUser(request, token));
    }

    // ====================== PROFILE VIEWS ======================
    @PostMapping("/views/{targetId}")
    public ResponseEntity<ProfileViewResponse> recordProfileView(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.recordProfileView(targetId, token));
    }
}