package com.premisave.listing.client;

import com.premisave.listing.dto.auth_service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Circuit-breaker fallback for AuthServiceClient.
 *
 * Fails safe rather than fails closed everywhere, except for identity: a
 * null from getCurrentUser()/getUserSummary() naturally makes
 * AdPromotionService's existing "user == null -> authentication failed"
 * check reject the request, so promotions/extensions correctly refuse to
 * proceed when identity can't be verified — the same outcome you'd want if
 * auth-service is actually down. Social features (likes, follows, reviews,
 * profile views) degrade to empty results/failed actions instead, since
 * those aren't security-critical.
 */
@Slf4j
@Component
public class AuthServiceClientFallback implements AuthServiceClient {

    private static final String UNAVAILABLE_MESSAGE = "Social features are temporarily unavailable.";

    @Override
    public UserSummaryResponse getCurrentUser(String token) {
        log.warn("auth-service unavailable (circuit open): getCurrentUser");
        return null;
    }

    @Override
    public UserSummaryResponse getUserSummary(String userId, String token) {
        log.warn("auth-service unavailable (circuit open): getUserSummary({})", userId);
        return null;
    }

    @Override
    public List<UserSummaryResponse> searchUsers(String query, String token) {
        log.warn("auth-service unavailable (circuit open): searchUsers");
        return List.of();
    }

    @Override
    public List<UserSummaryResponse> getAllUsers(String token) {
        log.warn("auth-service unavailable (circuit open): getAllUsers");
        return List.of();
    }

    @Override
    public SocialActionResponse likeUser(SocialActionRequest request, String token) {
        return unavailableAction("like");
    }

    @Override
    public SocialActionResponse unlikeUser(String targetId, String token) {
        return unavailableAction("unlike");
    }

    @Override
    public SocialActionResponse followUser(SocialActionRequest request, String token) {
        return unavailableAction("follow");
    }

    @Override
    public SocialActionResponse unfollowUser(String targetId, String token) {
        return unavailableAction("unfollow");
    }

    @Override
    public SocialActionResponse reviewUser(SocialActionRequest request, String token) {
        return unavailableAction("review");
    }

    @Override
    public SocialActionResponse editReview(SocialActionRequest request, String token) {
        return unavailableAction("edit-review");
    }

    @Override
    public SocialActionResponse deleteReview(String reviewId, String token) {
        return unavailableAction("delete-review");
    }

    @Override
    public List<ReviewResponse> getUserReviews(String targetId, String token) {
        log.warn("auth-service unavailable (circuit open): getUserReviews({})", targetId);
        return List.of();
    }

    @Override
    public UserInteractionResponse getUserSocialStats(String userId, String token) {
        log.warn("auth-service unavailable (circuit open): getUserSocialStats({})", userId);
        return new UserInteractionResponse();
    }

    @Override
    public List<UserSummaryResponse> getMyLikes(String token) {
        log.warn("auth-service unavailable (circuit open): getMyLikes");
        return List.of();
    }

    @Override
    public List<UserSummaryResponse> getMyFollowing(String token) {
        log.warn("auth-service unavailable (circuit open): getMyFollowing");
        return List.of();
    }

    @Override
    public ProfileViewResponse recordProfileView(String targetId, String token) {
        log.warn("auth-service unavailable (circuit open): recordProfileView({})", targetId);
        ProfileViewResponse response = new ProfileViewResponse();
        response.setMessage(UNAVAILABLE_MESSAGE);
        return response;
    }

    @Override
    public List<ProfileViewResponse> getWhoViewedMe(String token) {
        log.warn("auth-service unavailable (circuit open): getWhoViewedMe");
        return List.of();
    }

    @Override
    public List<WhoIViewedResponse> getWhoIViewed(String token) {
        log.warn("auth-service unavailable (circuit open): getWhoIViewed");
        return List.of();
    }

    @Override
    public Object getMyProfileViewStats(String token) {
        log.warn("auth-service unavailable (circuit open): getMyProfileViewStats");
        return Map.of("message", UNAVAILABLE_MESSAGE);
    }

    @Override
    public PublicProfileViewStats getOtherUserProfileViewStats(String userId, String token) {
        log.warn("auth-service unavailable (circuit open): getOtherUserProfileViewStats({})", userId);
        PublicProfileViewStats stats = new PublicProfileViewStats();
        stats.setMessage(UNAVAILABLE_MESSAGE);
        return stats;
    }

    private SocialActionResponse unavailableAction(String action) {
        log.warn("auth-service unavailable (circuit open): {}", action);
        SocialActionResponse response = new SocialActionResponse();
        response.setAction(action);
        response.setSuccess(false);
        response.setMessage(UNAVAILABLE_MESSAGE);
        return response;
    }
}