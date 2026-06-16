package com.premisave.listing.dto.auth_service;

import lombok.Data;

@Data
public class UserSummaryResponse {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String displayName;
}