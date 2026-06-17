package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.auth_service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private final AuthServiceClient authServiceClient;

    // ====================== SOCIAL ACTIONS ======================

    public SocialActionResponse likeUser(SocialActionRequest request, String token) {
        return authServiceClient.likeUser(request, token);
    }

    public SocialActionResponse unlikeUser(String targetId, String token) {
        return authServiceClient.unlikeUser(targetId, token);
    }

    public SocialActionResponse followUser(SocialActionRequest request, String token) {
        return authServiceClient.followUser(request, token);
    }

    public SocialActionResponse unfollowUser(String targetId, String token) {
        return authServiceClient.unfollowUser(targetId, token);
    }

    public SocialActionResponse reviewUser(SocialActionRequest request, String token) {
        return authServiceClient.reviewUser(request, token);
    }

    public SocialActionResponse editReview(SocialActionRequest request, String token) {
        return authServiceClient.editReview(request, token);
    }

    public SocialActionResponse deleteReview(String reviewId, String token) {
        return authServiceClient.deleteReview(reviewId, token);
    }

    public List<ReviewResponse> getUserReviews(String targetId, String token) {
        return authServiceClient.getUserReviews(targetId, token);
    }

    public UserInteractionResponse getUserSocialStats(String userId, String token) {
        return authServiceClient.getUserSocialStats(userId, token);
    }

    public List<UserSummaryResponse> getMyLikes(String token) {
        return authServiceClient.getMyLikes(token);
    }

    public List<UserSummaryResponse> getMyFollowing(String token) {
        return authServiceClient.getMyFollowing(token);
    }

    // ====================== PROFILE METHODS ======================

    public List<UserSummaryResponse> searchUsers(String query, String token) {
        log.info("Searching users with query: {}", query);
        return authServiceClient.searchUsers(query, token);
    }

    public List<UserSummaryResponse> getAllUsers(String token) {
        log.info("Fetching all users");
        return authServiceClient.getAllUsers(token);
    }

    public UserSummaryResponse getUserProfile(String userId, String token) {
        log.info("Fetching profile for user: {}", userId);
        return authServiceClient.getUserSummary(userId, token);
    }

    // ====================== PROFILE VIEWS ======================

    public ProfileViewResponse recordProfileView(String targetId, String token) {
        return authServiceClient.recordProfileView(targetId, token);
    }

    public List<ProfileViewResponse> getWhoViewedMe(String token) {
        return authServiceClient.getWhoViewedMe(token);
    }

    public List<WhoIViewedResponse> getWhoIViewed(String token) {
        return authServiceClient.getWhoIViewed(token);
    }

    public Object getMyProfileViewStats(String token) {
        return authServiceClient.getMyProfileViewStats(token);
    }

    public PublicProfileViewStats getOtherUserProfileViewStats(String userId, String token) {
        return authServiceClient.getOtherUserProfileViewStats(userId, token);
    }
}