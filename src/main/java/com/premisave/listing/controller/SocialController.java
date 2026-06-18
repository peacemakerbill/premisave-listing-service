package com.premisave.listing.controller;

import com.premisave.listing.dto.auth_service.*;
import com.premisave.listing.service.SocialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/social")
@RequiredArgsConstructor
public class SocialController {

    private final SocialService socialService;

    // ====================== PROFILE ENDPOINTS ======================

    @GetMapping("/profile/me")
    public ResponseEntity<UserSummaryResponse> getCurrentUserProfile(
            @RequestHeader("Authorization") String token) {
        
        log.info("Received request for /social/profile/me");
        UserSummaryResponse user = socialService.getCurrentUserProfile(token);
        return ResponseEntity.ok(user);
    }

    @GetMapping("/profile/user/{userId}")
    public ResponseEntity<UserSummaryResponse> getUserProfile(
            @PathVariable String userId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getUserProfile(userId, token));
    }

    @GetMapping("/profile/search")
    public ResponseEntity<List<UserSummaryResponse>> searchUsers(
            @RequestParam String query,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.searchUsers(query, token));
    }

    @GetMapping("/profile/all")
    public ResponseEntity<List<UserSummaryResponse>> getAllUsers(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getAllUsers(token));
    }

    // ====================== SOCIAL ACTIONS ======================
    @PostMapping("/like")
    public ResponseEntity<SocialActionResponse> likeUser(@RequestBody SocialActionRequest request,
                                                         @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.likeUser(request, token));
    }

    @DeleteMapping("/unlike/{targetId}")
    public ResponseEntity<SocialActionResponse> unlikeUser(@PathVariable String targetId,
                                                           @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unlikeUser(targetId, token));
    }

    @PostMapping("/follow")
    public ResponseEntity<SocialActionResponse> followUser(@RequestBody SocialActionRequest request,
                                                           @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.followUser(request, token));
    }

    @DeleteMapping("/unfollow/{targetId}")
    public ResponseEntity<SocialActionResponse> unfollowUser(@PathVariable String targetId,
                                                             @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.unfollowUser(targetId, token));
    }

    @PostMapping("/review")
    public ResponseEntity<SocialActionResponse> reviewUser(@RequestBody SocialActionRequest request,
                                                           @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.reviewUser(request, token));
    }

    @PutMapping("/review")
    public ResponseEntity<SocialActionResponse> editReview(@RequestBody SocialActionRequest request,
                                                           @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.editReview(request, token));
    }

    @DeleteMapping("/review/{reviewId}")
    public ResponseEntity<SocialActionResponse> deleteReview(@PathVariable String reviewId,
                                                             @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.deleteReview(reviewId, token));
    }

    @GetMapping("/reviews/{targetId}")
    public ResponseEntity<List<ReviewResponse>> getUserReviews(@PathVariable String targetId,
                                                               @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getUserReviews(targetId, token));
    }

    @GetMapping("/stats/{userId}")
    public ResponseEntity<UserInteractionResponse> getUserSocialStats(@PathVariable String userId,
                                                                      @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getUserSocialStats(userId, token));
    }

    @GetMapping("/my-likes")
    public ResponseEntity<List<UserSummaryResponse>> getMyLikes(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getMyLikes(token));
    }

    @GetMapping("/my-following")
    public ResponseEntity<List<UserSummaryResponse>> getMyFollowing(@RequestHeader("Authorization") String token) {
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
    public ResponseEntity<List<ProfileViewResponse>> getWhoViewedMe(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getWhoViewedMe(token));
    }

    @GetMapping("/views/who-i-viewed")
    public ResponseEntity<List<WhoIViewedResponse>> getWhoIViewed(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getWhoIViewed(token));
    }

    @GetMapping("/views/stats")
    public ResponseEntity<Object> getMyProfileViewStats(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getMyProfileViewStats(token));
    }

    @GetMapping("/views/stats/{userId}")
    public ResponseEntity<PublicProfileViewStats> getUserProfileViewStats(
            @PathVariable String userId,
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(socialService.getOtherUserProfileViewStats(userId, token));
    }
}