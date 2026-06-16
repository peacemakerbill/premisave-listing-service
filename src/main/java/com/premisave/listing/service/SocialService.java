package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.auth_service.ProfileViewResponse;
import com.premisave.listing.dto.auth_service.ProfileViewStats;
import com.premisave.listing.dto.auth_service.ReviewResponse;
import com.premisave.listing.dto.auth_service.SocialActionRequest;
import com.premisave.listing.dto.auth_service.SocialActionResponse;
import com.premisave.listing.dto.auth_service.UserInteractionResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.dto.auth_service.WhoIViewedResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialService {

    private final AuthServiceClient authServiceClient;

    // ====================== LIKE ======================
    public SocialActionResponse likeUser(SocialActionRequest request, String token) {
        return authServiceClient.likeUser(request, token);
    }

    public SocialActionResponse unlikeUser(String targetId, String token) {
        return authServiceClient.unlikeUser(targetId, token);
    }

    // ====================== FOLLOW ======================
    public SocialActionResponse followUser(SocialActionRequest request, String token) {
        return authServiceClient.followUser(request, token);
    }

    public SocialActionResponse unfollowUser(String targetId, String token) {
        return authServiceClient.unfollowUser(targetId, token);
    }

    // ====================== REVIEW ======================
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

    // ====================== USER SOCIAL LISTS ======================
    public List<UserSummaryResponse> getMyLikes(String token) {
        return authServiceClient.getMyLikes(token);
    }

    public List<UserSummaryResponse> getMyFollowing(String token) {
        return authServiceClient.getMyFollowing(token);
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

    public ProfileViewStats getMyProfileViewStats(String token) {
        return authServiceClient.getMyProfileViewStats(token);
    }
}