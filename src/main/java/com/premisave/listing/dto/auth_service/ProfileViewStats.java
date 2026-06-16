package com.premisave.listing.dto.auth_service;

import lombok.Data;

@Data
public class ProfileViewStats {
    private long totalViews;
    private long last7Days;
    private long last30Days;
    private int uniqueViewers;
    private String message;
}