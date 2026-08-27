package com.premisave.listing.dto.auth_service;

import lombok.Data;

/**
 * Mirrors auth-service's actual profile response fields. Previously only
 * declared 6 fields, so Jackson silently dropped everything else
 * (email, phone, address, role, etc.) during deserialization — no error,
 * just a smaller object than auth-service actually sent.
 *
 * "displayName" removed (auth-service never populates it). "password"
 * intentionally excluded (always null on auth-service's side anyway).
 */
@Data
public class UserSummaryResponse {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String middleName;
    private String lastName;
    private String fullName;
    private String phoneNumber;
    private String country;
    private String address1;
    private String address2;
    private String language;
    private String profilePictureUrl;
    private String role;
    private boolean active;
    private boolean verified;
    private boolean archived;
}