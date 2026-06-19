package com.premisave.listing.service;

import com.premisave.listing.entity.*;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    // ====================== READ ======================

    /**
     * Get all listings with optional filters.
     * Admin sees everything — deleted, archived, pending, active.
     */
    public List<Object> getAllListings(Boolean deleted, Boolean archived, ListingStatus status) {
        List<Object> allListings = new ArrayList<>();
        allListings.addAll(shortTermRentalRepository.findAll());
        allListings.addAll(longTermRentalRepository.findAll());
        allListings.addAll(houseSaleRepository.findAll());
        allListings.addAll(landSaleRepository.findAll());
        allListings.addAll(leaseRepository.findAll());

        return allListings.stream()
                .filter(obj -> obj instanceof Listing)
                .map(obj -> (Listing) obj)
                .filter(l -> deleted == null || l.isDeleted() == deleted)
                .filter(l -> archived == null || l.isArchived() == archived)
                .filter(l -> status == null || l.getStatus() == status)
                .map(l -> (Object) l)
                .toList();
    }

    public Object getListingById(String id) {
        return shortTermRentalRepository.findById(id)
                .map(l -> (Object) l)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElseThrow(() -> new RuntimeException("Listing not found with id: " + id));
    }

    // ====================== APPROVE / REJECT ======================

    @Transactional
    public String approveListing(String id) {
        Listing listing = findListingById(id);

        if (listing.isDeleted()) {
            throw new RuntimeException("Cannot approve a deleted listing");
        }
        if (listing.getStatus() == ListingStatus.ACTIVE) {
            throw new RuntimeException("Listing is already approved and active");
        }

        listing.setStatus(ListingStatus.ACTIVE);
        listing.setActive(true);
        saveListing(listing);

        log.info("Admin approved listing: {}", id);
        return "Listing " + id + " has been approved and is now ACTIVE";
    }

    @Transactional
    public String rejectListing(String id) {
        Listing listing = findListingById(id);

        if (listing.isDeleted()) {
            throw new RuntimeException("Cannot reject a deleted listing");
        }
        if (listing.getStatus() == ListingStatus.REJECTED) {
            throw new RuntimeException("Listing has already been rejected");
        }

        listing.setStatus(ListingStatus.REJECTED);
        listing.setActive(false);
        saveListing(listing);

        log.info("Admin rejected listing: {}", id);
        return "Listing " + id + " has been rejected";
    }

    // ====================== ARCHIVE / UNARCHIVE ======================

    @Transactional
    public String archiveListing(String id) {
        Listing listing = findListingById(id);

        if (listing.isDeleted()) {
            throw new RuntimeException("Cannot archive a deleted listing");
        }
        if (listing.isArchived()) {
            throw new RuntimeException("Listing is already archived");
        }

        listing.setArchived(true);
        listing.setActive(false);
        saveListing(listing);

        log.info("Admin archived listing: {}", id);
        return "Listing " + id + " has been archived";
    }

    @Transactional
    public String unarchiveListing(String id) {
        Listing listing = findListingById(id);

        if (listing.isDeleted()) {
            throw new RuntimeException("Cannot unarchive a deleted listing");
        }
        if (!listing.isArchived()) {
            throw new RuntimeException("Listing is not archived — nothing to unarchive");
        }

        listing.setArchived(false);
        listing.setActive(true);
        saveListing(listing);

        log.info("Admin unarchived listing: {}", id);
        return "Listing " + id + " has been unarchived";
    }

    // ====================== SOFT DELETE / RESTORE ======================

    @Transactional
    public String softDeleteListing(String id) {
        Listing listing = findListingById(id);

        if (listing.isDeleted()) {
            throw new RuntimeException("Listing has already been deleted");
        }

        listing.setDeleted(true);
        listing.setDeletedAt(LocalDateTime.now());
        listing.setActive(false);
        saveListing(listing);

        log.info("Admin soft deleted listing: {}", id);
        return "Listing " + id + " has been deleted";
    }

    /**
     * Restore a soft-deleted listing back to ACTIVE.
     * Only admins can do this.
     */
    @Transactional
    public String restoreListing(String id) {
        Listing listing = findListingById(id);

        if (!listing.isDeleted()) {
            throw new RuntimeException("Listing is not deleted — nothing to restore");
        }

        listing.setDeleted(false);
        listing.setDeletedAt(null);
        listing.setActive(true);
        listing.setArchived(false);
        listing.setStatus(ListingStatus.ACTIVE);
        saveListing(listing);

        log.info("Admin restored listing: {}", id);
        return "Listing " + id + " has been restored and is now ACTIVE";
    }

    /**
     * Permanently delete a listing from the database.
     * Irreversible — use with caution.
     */
    @Transactional
    public String hardDeleteListing(String id) {
        Listing listing = findListingById(id);

        if (listing instanceof ShortTermRental st) shortTermRentalRepository.delete(st);
        else if (listing instanceof LongTermRental lt) longTermRentalRepository.delete(lt);
        else if (listing instanceof HouseSale hs) houseSaleRepository.delete(hs);
        else if (listing instanceof LandSale ls) landSaleRepository.delete(ls);
        else if (listing instanceof Lease l) leaseRepository.delete(l);

        log.warn("Admin HARD DELETED listing: {}", id);
        return "Listing " + id + " has been permanently deleted";
    }

    // ====================== HELPERS ======================

    private Listing findListingById(String id) {
        return (Listing) shortTermRentalRepository.findById(id)
                .map(l -> (Object) l)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElseThrow(() -> new RuntimeException("Listing not found with id: " + id));
    }

    private void saveListing(Listing listing) {
        if (listing instanceof ShortTermRental st) shortTermRentalRepository.save(st);
        else if (listing instanceof LongTermRental lt) longTermRentalRepository.save(lt);
        else if (listing instanceof HouseSale hs) houseSaleRepository.save(hs);
        else if (listing instanceof LandSale ls) landSaleRepository.save(ls);
        else if (listing instanceof Lease l) leaseRepository.save(l);
    }
}