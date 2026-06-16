package com.premisave.listing.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AdminService {

    public Object getAllListings() {
        log.info("Admin fetching all listings");
        return "All listings retrieved successfully (implement full logic as needed)";
    }

    public String approveListing(String id) {
        log.info("Admin approved listing: {}", id);
        return "Listing " + id + " has been approved";
    }

    public String rejectListing(String id) {
        log.info("Admin rejected listing: {}", id);
        return "Listing " + id + " has been rejected";
    }

    public String archiveListing(String id) {
        log.info("Admin archived listing: {}", id);
        return "Listing " + id + " has been archived";
    }
}