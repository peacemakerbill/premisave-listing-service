package com.premisave.listing.controller;

import com.premisave.listing.dto.auth_service.*;
import com.premisave.listing.service.SocialService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ====================== SOCIAL ACTIONS ======================

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

    @PutMapping("/review")
    public ResponseEntity<SocialActionResponse> editReview(
            @RequestBody SocialActionRequest request,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.editReview(request, token));
    }

    @DeleteMapping("/review/{reviewId}")
    public ResponseEntity<SocialActionResponse> deleteReview(
            @PathVariable String reviewId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.deleteReview(reviewId, token));
    }

    @GetMapping("/reviews/{targetId}")
    public ResponseEntity<List<ReviewResponse>> getUserReviews(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getUserReviews(targetId, token));
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<UserInteractionResponse> getUserSocialStats(
            @PathVariable String userId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getUserSocialStats(userId, token));
    }

    @GetMapping("/my-likes")
    public ResponseEntity<?> getMyLikes(@RequestHeader("Authorization") String token) {
        // Implement in SocialService if needed
        return ResponseEntity.ok(socialService.getMyLikes(token));
    }

    @GetMapping("/my-following")
    public ResponseEntity<?> getMyFollowing(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getMyFollowing(token));
    }

    // ====================== PROFILE VIEWS ======================

    @PostMapping("/views/{targetId}")
    public ResponseEntity<ProfileViewResponse> recordProfileView(
            @PathVariable String targetId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.recordProfileView(targetId, token));
    }

    @GetMapping("/views/who-viewed-me")
    public ResponseEntity<List<ProfileViewResponse>> getWhoViewedMe(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getWhoViewedMe(token));
    }

    @GetMapping("/views/who-i-viewed")
    public ResponseEntity<List<WhoIViewedResponse>> getWhoIViewed(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getWhoIViewed(token));
    }

    @GetMapping("/views/stats")
    public ResponseEntity<?> getProfileViewStats(
            @RequestParam(required = false) String userId,
            @RequestHeader("Authorization") String token) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.ok(socialService.getMyProfileViewStats(token));
        }
        return ResponseEntity.ok(socialService.getOtherUserProfileViewStats(userId, token));
    }

    @GetMapping("/views/stats/{userId}")
    public ResponseEntity<PublicProfileViewStats> getUserProfileViewStats(
            @PathVariable String userId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getOtherUserProfileViewStats(userId, token));
    }
}