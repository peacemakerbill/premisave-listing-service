package com.premisave.listing.controller;

import com.premisave.listing.dto.auth_service.ProfileViewResponse;
import com.premisave.listing.dto.auth_service.SocialActionRequest;
import com.premisave.listing.dto.auth_service.SocialActionResponse;
import com.premisave.listing.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ====================== SOCIAL FEATURES ======================
    @PostMapping("/like")
    public ResponseEntity<SocialActionResponse> likeUser(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.likeUser(request, token));
    }

    @DeleteMapping("/unlike/{targetId}")
    public ResponseEntity<SocialActionResponse> unlikeUser(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unlikeUser(targetId, token));
    }

    @PostMapping("/follow")
    public ResponseEntity<SocialActionResponse> followUser(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.followUser(request, token));
    }

    @DeleteMapping("/unfollow/{targetId}")
    public ResponseEntity<SocialActionResponse> unfollowUser(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unfollowUser(targetId, token));
    }

    @PostMapping("/review")
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