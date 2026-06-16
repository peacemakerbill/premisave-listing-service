package com.premisave.listing.dto.auth_service;

import lombok.Data;

@Data
public class UserInteractionResponse {
    private long followerCount;
    private long followingCount;
    private long likeCount;
    private double averageRating;
    private int totalReviews;
}