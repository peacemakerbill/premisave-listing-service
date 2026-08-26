package com.premisave.listing.client;

import com.premisave.listing.dto.auth_service.*;
import com.premisave.listing.exception.AuthServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * FallbackFactory (rather than a plain fallback class) so we get access to
 * the actual Throwable that triggered the fallback. A Feign fallback fires
 * on ANY failed call — connection refused, a timeout, a 5xx response, or a
 * genuinely open circuit breaker — not only when the breaker has actually
 * tripped open. The previous plain-fallback version hardcoded "circuit
 * open" in every log line regardless of which of those actually happened,
 * which is misleading exactly when accurate diagnosis matters most.
 *
 * Fails safe rather than fails closed for social features, but fails
 * loudly and correctly for identity: getCurrentUser() now throws
 * AuthServiceUnavailableException instead of returning null when
 * auth-service is unreachable, so callers (AdPromotionService,
 * ListingService) get a correct "service unavailable" 503 rather than a
 * misleading "please log in again" 401 — the null-check pattern those
 * callers use for a genuine "no such user" response from auth-service
 * still works exactly as before, since this exception is thrown before
 * that check is ever reached. Social features (likes, follows, reviews,
 * profile views) still degrade to empty results/failed actions instead,
 * since those aren't security-critical.
 */
@Slf4j
@Component
public class AuthServiceClientFallbackFactory implements FallbackFactory<AuthServiceClient> {

    private static final String UNAVAILABLE_MESSAGE = "Social features are temporarily unavailable.";

    @Override
    public AuthServiceClient create(Throwable cause) {
        return new AuthServiceClient() {

            @Override
            public UserSummaryResponse getCurrentUser(String token) {
                logFallback("getCurrentUser", cause);
                // Throws rather than returning null: a null return here
                // used to be indistinguishable from auth-service genuinely
                // rejecting the token, so callers (ListingService,
                // AdPromotionService) reported "User authentication failed.
                // Please login again." even when the real problem was
                // auth-service being unreachable — a misleading message,
                // since logging in again wouldn't have helped.
                throw new AuthServiceUnavailableException(
                        "Authentication service is currently unavailable. Please try again shortly.");
            }

            @Override
            public UserSummaryResponse getUserSummary(String userId, String token) {
                logFallback("getUserSummary(" + userId + ")", cause);
                return null;
            }

            @Override
            public List<UserSummaryResponse> searchUsers(String query, String token) {
                logFallback("searchUsers", cause);
                return List.of();
            }

            @Override
            public List<UserSummaryResponse> getAllUsers(String token) {
                logFallback("getAllUsers", cause);
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
                logFallback("getUserReviews(" + targetId + ")", cause);
                return List.of();
            }

            @Override
            public UserInteractionResponse getUserSocialStats(String userId, String token) {
                logFallback("getUserSocialStats(" + userId + ")", cause);
                return new UserInteractionResponse();
            }

            @Override
            public List<UserSummaryResponse> getMyLikes(String token) {
                logFallback("getMyLikes", cause);
                return List.of();
            }

            @Override
            public List<UserSummaryResponse> getMyFollowing(String token) {
                logFallback("getMyFollowing", cause);
                return List.of();
            }

            @Override
            public ProfileViewResponse recordProfileView(String targetId, String token) {
                logFallback("recordProfileView(" + targetId + ")", cause);
                ProfileViewResponse response = new ProfileViewResponse();
                response.setMessage(UNAVAILABLE_MESSAGE);
                return response;
            }

            @Override
            public List<ProfileViewResponse> getWhoViewedMe(String token) {
                logFallback("getWhoViewedMe", cause);
                return List.of();
            }

            @Override
            public List<WhoIViewedResponse> getWhoIViewed(String token) {
                logFallback("getWhoIViewed", cause);
                return List.of();
            }

            @Override
            public Object getMyProfileViewStats(String token) {
                logFallback("getMyProfileViewStats", cause);
                return Map.of("message", UNAVAILABLE_MESSAGE);
            }

            @Override
            public PublicProfileViewStats getOtherUserProfileViewStats(String userId, String token) {
                logFallback("getOtherUserProfileViewStats(" + userId + ")", cause);
                PublicProfileViewStats stats = new PublicProfileViewStats();
                stats.setMessage(UNAVAILABLE_MESSAGE);
                return stats;
            }

            private SocialActionResponse unavailableAction(String action) {
                logFallback(action, cause);
                SocialActionResponse response = new SocialActionResponse();
                response.setAction(action);
                response.setSuccess(false);
                response.setMessage(UNAVAILABLE_MESSAGE);
                return response;
            }
        };
    }

    private void logFallback(String method, Throwable cause) {
        log.warn("auth-service call failed, using fallback: {} — {}: {}",
                method, cause.getClass().getSimpleName(), cause.getMessage());
    }
}