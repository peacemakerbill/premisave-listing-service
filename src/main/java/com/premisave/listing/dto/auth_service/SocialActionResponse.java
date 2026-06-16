package com.premisave.listing.dto.auth_service;

import lombok.Data;

@Data
public class SocialActionResponse {
    private String action;
    private String message;
    private boolean success;
}