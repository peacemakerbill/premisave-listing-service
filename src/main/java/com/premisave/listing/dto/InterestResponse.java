package com.premisave.listing.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InterestResponse {
    private String id;
    private String listingId;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String address;
    private String message;
    private LocalDateTime createdAt;
}