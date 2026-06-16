package com.premisave.listing.dto.auth_service;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewResponse {
    private String id;
    private String userId;
    private String targetId;
    private Integer rating;
    private String comment;
    private LocalDateTime createdAt;
}
