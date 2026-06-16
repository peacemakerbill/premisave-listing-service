package com.premisave.listing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminService {

    public Object getAllListings() {
        return "All listings retrieved (Admin)";
    }

    public String approveListing(String id) {
        return "Listing " + id + " approved";
    }

    public String rejectListing(String id) {
        return "Listing " + id + " rejected";
    }
}