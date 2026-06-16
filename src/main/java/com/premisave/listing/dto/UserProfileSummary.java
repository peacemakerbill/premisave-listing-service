package com.premisave.listing.dto;

import lombok.Data;

@Data
public class UserProfileSummary {

    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String profilePictureUrl;
    private String email;
    private String phoneNumber;
    private String country;
}