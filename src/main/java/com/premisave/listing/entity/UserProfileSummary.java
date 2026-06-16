package com.premisave.listing.entity;

import lombok.Data;

@Data
public class UserProfileSummary {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String email;
}