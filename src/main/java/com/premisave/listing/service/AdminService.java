package com.premisave.listing.service;

import com.premisave.listing.entity.*;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ShortTermRentalRepository shortTermRentalRepository;
    private final LongTermRentalRepository longTermRentalRepository;
    private final HouseSaleRepository houseSaleRepository;
    private final LandSaleRepository landSaleRepository;
    private final LeaseRepository leaseRepository;

    public List<Object> getAllListings() {
        List<Object> allListings = new ArrayList<>();

        allListings.addAll(shortTermRentalRepository.findAll());
        allListings.addAll(longTermRentalRepository.findAll());
        allListings.addAll(houseSaleRepository.findAll());
        allListings.addAll(landSaleRepository.findAll());
        allListings.addAll(leaseRepository.findAll());

        log.info("Admin fetched {} total listings", allListings.size());
        return allListings;
    }

    @Transactional
    public String approveListing(String id) {
        Listing listing = findListingById(id);
        if (listing == null) {
            return "Listing not found with id: " + id;
        }

        listing.setStatus(ListingStatus.ACTIVE);
        saveListing(listing);
        
        log.info("Admin approved listing: {}", id);
        return "Listing " + id + " has been approved and is now ACTIVE";
    }

    @Transactional
    public String rejectListing(String id) {
        Listing listing = findListingById(id);
        if (listing == null) {
            return "Listing not found with id: " + id;
        }

        listing.setStatus(ListingStatus.REJECTED);
        listing.setActive(false);
        saveListing(listing);

        log.info("Admin rejected listing: {}", id);
        return "Listing " + id + " has been rejected";
    }

    @Transactional
    public String archiveListing(String id) {
        Listing listing = findListingById(id);
        if (listing == null) {
            return "Listing not found with id: " + id;
        }

        listing.setArchived(true);
        listing.setActive(false);
        saveListing(listing);

        log.info("Admin archived listing: {}", id);
        return "Listing " + id + " has been archived";
    }

    private Listing findListingById(String id) {
        return (Listing) shortTermRentalRepository.findById(id)
                .map(l -> (Object) l)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElse(null);
    }

    private void saveListing(Listing listing) {
        if (listing instanceof ShortTermRental st) {
            shortTermRentalRepository.save(st);
        } else if (listing instanceof LongTermRental lt) {
            longTermRentalRepository.save(lt);
        } else if (listing instanceof HouseSale hs) {
            houseSaleRepository.save(hs);
        } else if (listing instanceof LandSale ls) {
            landSaleRepository.save(ls);
        } else if (listing instanceof Lease l) {
            leaseRepository.save(l);
        }
    }
}