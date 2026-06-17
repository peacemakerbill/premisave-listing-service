package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.*;
import com.premisave.listing.enums.ListingCategory;
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
public class ListingService {

    private final ShortTermRentalRepository shortTermRentalRepository;
    private final LongTermRentalRepository longTermRentalRepository;
    private final LandSaleRepository landSaleRepository;
    private final HouseSaleRepository houseSaleRepository;
    private final LeaseRepository leaseRepository;
    private final AuthServiceClient authServiceClient;

    @Transactional
    public ListingResponse createListing(ListingRequest request, String authorizationHeader) {
        UserSummaryResponse user = authServiceClient.getCurrentUser(authorizationHeader);
        if (user == null) {
            throw new RuntimeException("User not authenticated");
        }

        Listing listing = createSpecificListing(request);

        // Set common fields BEFORE saving
        listing.setOwnerId(user.getId());
        listing.setTitle(request.getTitle());
        listing.setDescription(request.getDescription());
        listing.setCategory(request.getCategory());
        listing.setPrice(request.getPrice());
        listing.setLatitude(request.getLatitude());
        listing.setLongitude(request.getLongitude());
        listing.setAddress(request.getAddress());
        listing.setCity(request.getCity());
        listing.setCountry(request.getCountry());
        listing.setMainImageUrl(request.getMainImageUrl());
        listing.setImageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>());
        listing.setStatus(ListingStatus.ACTIVE);

        listing = saveListing(listing);

        log.info("New listing created: {} by user {}", listing.getId(), user.getId());

        return new ListingResponse("Listing created successfully", listing.getId(), listing.getTitle(), true);
    }

    private Listing createSpecificListing(ListingRequest request) {
        return switch (request.getCategory()) {
            case SHORT_TERM_RENTAL -> createShortTermRental(request);
            case LONG_TERM_RENTAL -> createLongTermRental(request);
            case LAND_SALE -> createLandSale(request);
            case HOUSE_SALE -> createHouseSale(request);
            case LEASE -> createLease(request);
        };
    }

    private ShortTermRental createShortTermRental(ListingRequest r) {
        ShortTermRental st = new ShortTermRental();
        st.setMaxGuests(r.getMaxGuests() != null ? r.getMaxGuests() : 1);
        st.setBedrooms(r.getBedrooms() != null ? r.getBedrooms() : 1);
        st.setBathrooms(r.getBathrooms() != null ? r.getBathrooms() : 1);
        st.setHasWifi(Boolean.TRUE.equals(r.getHasWifi()));
        st.setHasKitchen(Boolean.TRUE.equals(r.getHasKitchen()));
        st.setAmenities(r.getAmenities() != null ? r.getAmenities() : new ArrayList<>());
        return st;
    }

    private LongTermRental createLongTermRental(ListingRequest r) {
        LongTermRental lt = new LongTermRental();
        lt.setMinLeaseMonths(r.getMinLeaseMonths() != null ? r.getMinLeaseMonths() : 6);
        lt.setFurnished(Boolean.TRUE.equals(r.getFurnished()));
        lt.setTenantRequirements(r.getTenantRequirements());
        return lt;
    }

    private LandSale createLandSale(ListingRequest r) {
        LandSale ls = new LandSale();
        ls.setSizeInAcres(r.getSizeInAcres() != null ? r.getSizeInAcres() : 0.0);
        ls.setLandUseType(r.getLandUseType());
        ls.setHasTitleDeed(Boolean.TRUE.equals(r.getHasTitleDeed()));
        return ls;
    }

    private HouseSale createHouseSale(ListingRequest r) {
        HouseSale hs = new HouseSale();
        hs.setBedrooms(r.getBedrooms() != null ? r.getBedrooms() : 0);
        hs.setBathrooms(r.getBathrooms() != null ? r.getBathrooms() : 0);
        hs.setFloors(r.getFloors() != null ? r.getFloors() : 1);
        hs.setPlotSize(r.getPlotSize() != null ? r.getPlotSize() : 0.0);
        hs.setHasGarage(Boolean.TRUE.equals(r.getHasGarage()));
        hs.setPropertyType(r.getPropertyType());
        return hs;
    }

    private Lease createLease(ListingRequest r) {
        Lease lease = new Lease();
        lease.setLeaseDurationMonths(r.getLeaseDurationMonths() != null ? r.getLeaseDurationMonths() : 12);
        lease.setDepositAmount(r.getDepositAmount());
        lease.setLeaseTerms(r.getLeaseTerms());
        lease.setRenewable(Boolean.TRUE.equals(r.getRenewable()));
        return lease;
    }

    private Listing saveListing(Listing listing) {
        return switch (listing) {
            case ShortTermRental st -> shortTermRentalRepository.save(st);
            case LongTermRental lt -> longTermRentalRepository.save(lt);
            case LandSale ls -> landSaleRepository.save(ls);
            case HouseSale hs -> houseSaleRepository.save(hs);
            case Lease l -> leaseRepository.save(l);
            default -> throw new IllegalArgumentException("Unknown listing type: " + listing.getClass().getSimpleName());
        };
    }

    public Object getListingById(String id) {
        return shortTermRentalRepository.findById(id)
                .map(listing -> (Object) listing)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElseThrow(() -> new RuntimeException("Listing not found with id: " + id));
    }

    @Transactional
    public ListingResponse updateListing(String id, ListingRequest request, String userId) {
        Listing existing = (Listing) getListingById(id);

        if (!existing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only update your own listings");
        }

        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setPrice(request.getPrice());
        existing.setLatitude(request.getLatitude());
        existing.setLongitude(request.getLongitude());
        existing.setAddress(request.getAddress());
        existing.setCity(request.getCity());
        existing.setCountry(request.getCountry());
        existing.setMainImageUrl(request.getMainImageUrl());
        if (request.getImageUrls() != null) {
            existing.setImageUrls(request.getImageUrls());
        }

        updateSpecificFields(existing, request);

        Listing saved = saveListing(existing);
        return new ListingResponse("Listing updated successfully", saved.getId(), saved.getTitle(), true);
    }

    private void updateSpecificFields(Listing listing, ListingRequest request) {
        if (listing instanceof ShortTermRental st) {
            if (request.getMaxGuests() != null) st.setMaxGuests(request.getMaxGuests());
            if (request.getBedrooms() != null) st.setBedrooms(request.getBedrooms());
            if (request.getBathrooms() != null) st.setBathrooms(request.getBathrooms());
            if (request.getHasWifi() != null) st.setHasWifi(request.getHasWifi());
            if (request.getHasKitchen() != null) st.setHasKitchen(request.getHasKitchen());
            if (request.getAmenities() != null) st.setAmenities(request.getAmenities());

        } else if (listing instanceof LongTermRental lt) {
            if (request.getMinLeaseMonths() != null) lt.setMinLeaseMonths(request.getMinLeaseMonths());
            if (request.getFurnished() != null) lt.setFurnished(request.getFurnished());
            if (request.getTenantRequirements() != null) lt.setTenantRequirements(request.getTenantRequirements());

        } else if (listing instanceof LandSale ls) {
            if (request.getSizeInAcres() != null) ls.setSizeInAcres(request.getSizeInAcres());
            if (request.getLandUseType() != null) ls.setLandUseType(request.getLandUseType());
            if (request.getHasTitleDeed() != null) ls.setHasTitleDeed(request.getHasTitleDeed());

        } else if (listing instanceof HouseSale hs) {
            if (request.getBedrooms() != null) hs.setBedrooms(request.getBedrooms());
            if (request.getBathrooms() != null) hs.setBathrooms(request.getBathrooms());
            if (request.getFloors() != null) hs.setFloors(request.getFloors());
            if (request.getPlotSize() != null) hs.setPlotSize(request.getPlotSize());
            if (request.getHasGarage() != null) hs.setHasGarage(request.getHasGarage());
            if (request.getPropertyType() != null) hs.setPropertyType(request.getPropertyType());

        } else if (listing instanceof Lease lease) {
            if (request.getLeaseDurationMonths() != null) lease.setLeaseDurationMonths(request.getLeaseDurationMonths());
            if (request.getDepositAmount() != null) lease.setDepositAmount(request.getDepositAmount());
            if (request.getLeaseTerms() != null) lease.setLeaseTerms(request.getLeaseTerms());
            if (request.getRenewable() != null) lease.setRenewable(request.getRenewable());
        }
    }

    @Transactional
    public String deleteListing(String id, String userId) {
        Listing listing = (Listing) getListingById(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only delete your own listings");
        }

        listing.setActive(false);
        listing.setArchived(true);
        saveListing(listing);
        return "Listing has been archived successfully";
    }

    public List<ShortTermRental> getShortTermRentals(String city) {
        if (city == null || city.trim().isEmpty()) {
            return shortTermRentalRepository.findAll();
        }
        return shortTermRentalRepository.findByCityAndActiveTrue(city);
    }

    public List<?> getListingsByOwner(String ownerId, ListingCategory category) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return List.of();
        }

        return switch (category) {
            case SHORT_TERM_RENTAL -> shortTermRentalRepository.findByOwnerId(ownerId);
            case LONG_TERM_RENTAL -> longTermRentalRepository.findByOwnerId(ownerId);
            case LAND_SALE -> landSaleRepository.findByOwnerId(ownerId);
            case HOUSE_SALE -> houseSaleRepository.findByOwnerId(ownerId);
            case LEASE -> leaseRepository.findByOwnerId(ownerId);
        };
    }

    public List<?> getListingsByCategory(ListingCategory category, String city) {
        if (category == null) return List.of();

        return switch (category) {
            case SHORT_TERM_RENTAL -> {
                if (city != null && !city.trim().isEmpty()) {
                    yield shortTermRentalRepository.findByCityAndActiveTrue(city);
                } else {
                    yield shortTermRentalRepository.findAll();
                }
            }
            case LONG_TERM_RENTAL -> longTermRentalRepository.findAll();
            case LAND_SALE -> landSaleRepository.findAll();
            case HOUSE_SALE -> houseSaleRepository.findAll();
            case LEASE -> leaseRepository.findAll();
        };
    }

    public List<?> searchListings(String query, ListingCategory category, Double minPrice, Double maxPrice, String city) {
        List<Object> results = new ArrayList<>();

        if (category != null) {
            results.addAll(getListingsByCategory(category, city));
        } else {
            // Search across all types if no category specified
            if (city != null && !city.trim().isEmpty()) {
                results.addAll(shortTermRentalRepository.findByCityAndActiveTrue(city));
            } else {
                results.addAll(shortTermRentalRepository.findAll());
                results.addAll(longTermRentalRepository.findAll());
                results.addAll(houseSaleRepository.findAll());
                results.addAll(landSaleRepository.findAll());
                results.addAll(leaseRepository.findAll());
            }
        }

        log.info("Search executed with params: query={}, category={}, price range={}-{}, city={}", 
                query, category, minPrice, maxPrice, city);

        return results;
    }
}