package com.premisave.listing.service;

import com.cloudinary.Cloudinary;
import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.ListingRequest;
import com.premisave.listing.dto.ListingUpdateRequest;
import com.premisave.listing.dto.ListingResponse;
import com.premisave.listing.dto.MyListingResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.*;
import com.premisave.listing.enums.ListingCategory;
import com.premisave.listing.enums.ListingStatus;
import com.premisave.listing.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final Cloudinary cloudinary;

    @Value("${ad.promotion.daily-rate:2.99}")
    private BigDecimal dailyRate;

    // ====================== CREATE ======================

    @Transactional
    public ListingResponse createListing(ListingRequest request, String authorizationHeader) {
        try {
            UserSummaryResponse user = authServiceClient.getCurrentUser(authorizationHeader);
            if (user == null || user.getId() == null) {
                throw new RuntimeException("User authentication failed. Please login again.");
            }

            if (request.getImageUrls() == null) {
                request.setImageUrls(new ArrayList<>());
            }

            Listing listing = createSpecificListing(request);

            listing.setOwnerId(user.getId());
            listing.setTitle(request.getTitle());
            listing.setDescription(request.getDescription());
            listing.setCategory(request.getCategory());
            listing.setPrice(request.getPrice() != null ? request.getPrice() : BigDecimal.ZERO);
            listing.setLatitude(request.getLatitude());
            listing.setLongitude(request.getLongitude());
            listing.setAddress(request.getAddress());
            listing.setCity(request.getCity());
            listing.setCountry(request.getCountry() != null ? request.getCountry() : "Kenya");
            listing.setMainImageUrl(request.getMainImageUrl());
            listing.setImageUrls(request.getImageUrls());
            listing.setStatus(ListingStatus.ACTIVE);

            listing = saveListing(listing);

            log.info("New listing created successfully: ID={} by user={}", listing.getId(), user.getId());

            return new ListingResponse("Listing created successfully", listing.getId(), listing.getTitle(), true);

        } catch (Exception e) {
            log.error("Error while creating listing", e);
            throw new RuntimeException("Failed to create listing: " + e.getMessage(), e);
        }
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

    // ====================== UPDATE ======================

    @Transactional
    public ListingResponse updateListing(String id, ListingUpdateRequest request, String userId) {
        try {
            Listing existing = (Listing) getListingById(id);
            if (!existing.getOwnerId().equals(userId)) {
                throw new RuntimeException("You can only update your own listings");
            }

            // Only update fields that are provided
            if (request.getTitle() != null) existing.setTitle(request.getTitle());
            if (request.getDescription() != null) existing.setDescription(request.getDescription());
            if (request.getPrice() != null) existing.setPrice(request.getPrice());
            if (request.getLatitude() != null) existing.setLatitude(request.getLatitude());
            if (request.getLongitude() != null) existing.setLongitude(request.getLongitude());
            if (request.getAddress() != null) existing.setAddress(request.getAddress());
            if (request.getCity() != null) existing.setCity(request.getCity());
            if (request.getCountry() != null) existing.setCountry(request.getCountry());
            if (request.getCategory() != null) existing.setCategory(request.getCategory());

            if (request.getMainImageUrl() != null && !request.getMainImageUrl().isBlank()) {
                existing.setMainImageUrl(request.getMainImageUrl());
            }

            if (request.getImageUrls() != null) {
                existing.setImageUrls(request.getImageUrls());
            }

            updateSpecificFields(existing, request);
            Listing saved = saveListing(existing);

            log.info("Listing updated: {} by user {}", saved.getId(), userId);

            return new ListingResponse("Listing updated successfully", saved.getId(), saved.getTitle(), true);
        } catch (Exception e) {
            log.error("Error updating listing {}", id, e);
            throw new RuntimeException("Failed to update listing: " + e.getMessage(), e);
        }
    }

    // ====================== SAVE ======================

    public Listing saveListing(Listing listing) {
        return switch (listing) {
            case ShortTermRental st -> shortTermRentalRepository.save(st);
            case LongTermRental lt -> longTermRentalRepository.save(lt);
            case LandSale ls -> landSaleRepository.save(ls);
            case HouseSale hs -> houseSaleRepository.save(hs);
            case Lease l -> leaseRepository.save(l);
            default -> throw new IllegalArgumentException("Unknown listing type: " + listing.getClass().getSimpleName());
        };
    }

    // ====================== IMAGE UPLOAD ======================

    public String uploadImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            Map<String, Object> uploadParams = new HashMap<>();
            uploadParams.put("folder", "premisave/listings");
            uploadParams.put("resource_type", "auto");

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), uploadParams);
            String url = (String) uploadResult.get("secure_url");
            log.info("Image uploaded successfully: {}", url);
            return url;
        } catch (Exception e) {
            log.error("Cloudinary upload failed for file: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Image upload failed: " + e.getMessage());
        }
    }

    public List<String> uploadImages(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        return files.stream()
                .map(file -> {
                    try {
                        return uploadImage(file);
                    } catch (Exception e) {
                        log.warn("Failed to upload image {}: {}", file.getOriginalFilename(), e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .toList();
    }

    // ====================== GET ======================

    public Object getListingById(String id) {
        return shortTermRentalRepository.findById(id)
                .map(l -> (Object) l)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElseThrow(() -> new RuntimeException("Listing not found with id: " + id));
    }

    // ====================== UPDATE SPECIFIC FIELDS ======================

    private void updateSpecificFields(Listing listing, ListingUpdateRequest request) {
        if (listing instanceof ShortTermRental st) updateShortTermRental(st, request);
        else if (listing instanceof LongTermRental lt) updateLongTermRental(lt, request);
        else if (listing instanceof LandSale ls) updateLandSale(ls, request);
        else if (listing instanceof HouseSale hs) updateHouseSale(hs, request);
        else if (listing instanceof Lease lease) updateLease(lease, request);
    }

    private void updateShortTermRental(ShortTermRental st, ListingUpdateRequest r) {
        if (r.getMaxGuests() != null) st.setMaxGuests(r.getMaxGuests());
        if (r.getBedrooms() != null) st.setBedrooms(r.getBedrooms());
        if (r.getBathrooms() != null) st.setBathrooms(r.getBathrooms());
        if (r.getHasWifi() != null) st.setHasWifi(r.getHasWifi());
        if (r.getHasKitchen() != null) st.setHasKitchen(r.getHasKitchen());
        if (r.getAmenities() != null) st.setAmenities(r.getAmenities());
    }

    private void updateLongTermRental(LongTermRental lt, ListingUpdateRequest r) {
        if (r.getMinLeaseMonths() != null) lt.setMinLeaseMonths(r.getMinLeaseMonths());
        if (r.getFurnished() != null) lt.setFurnished(r.getFurnished());
        if (r.getTenantRequirements() != null) lt.setTenantRequirements(r.getTenantRequirements());
    }

    private void updateLandSale(LandSale ls, ListingUpdateRequest r) {
        if (r.getSizeInAcres() != null) ls.setSizeInAcres(r.getSizeInAcres());
        if (r.getLandUseType() != null) ls.setLandUseType(r.getLandUseType());
        if (r.getHasTitleDeed() != null) ls.setHasTitleDeed(r.getHasTitleDeed());
    }

    private void updateHouseSale(HouseSale hs, ListingUpdateRequest r) {
        if (r.getBedrooms() != null) hs.setBedrooms(r.getBedrooms());
        if (r.getBathrooms() != null) hs.setBathrooms(r.getBathrooms());
        if (r.getFloors() != null) hs.setFloors(r.getFloors());
        if (r.getPlotSize() != null) hs.setPlotSize(r.getPlotSize());
        if (r.getHasGarage() != null) hs.setHasGarage(r.getHasGarage());
        if (r.getPropertyType() != null) hs.setPropertyType(r.getPropertyType());
    }

    private void updateLease(Lease lease, ListingUpdateRequest r) {
        if (r.getLeaseDurationMonths() != null) lease.setLeaseDurationMonths(r.getLeaseDurationMonths());
        if (r.getDepositAmount() != null) lease.setDepositAmount(r.getDepositAmount());
        if (r.getLeaseTerms() != null) lease.setLeaseTerms(r.getLeaseTerms());
        if (r.getRenewable() != null) lease.setRenewable(r.getRenewable());
    }

    // ====================== DELETE ======================

    @Transactional
    public String deleteListing(String id, String userId) {
        Listing listing = (Listing) getListingById(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only delete your own listings");
        }
        if (listing.isDeleted()) {
            throw new RuntimeException("Listing has already been deleted");
        }
        listing.setDeleted(true);
        listing.setDeletedAt(LocalDateTime.now());
        listing.setActive(false);
        saveListing(listing);
        return "Listing deleted successfully";
    }

    @Transactional
    public String archiveListing(String id, String userId) {
        Listing listing = (Listing) getListingById(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only archive your own listings");
        }
        if (listing.isDeleted()) {
            throw new RuntimeException("Listing has been deleted and cannot be archived");
        }
        if (listing.isArchived()) {
            throw new RuntimeException("Listing is already archived");
        }
        listing.setArchived(true);
        listing.setActive(false);
        saveListing(listing);
        return "Listing archived successfully";
    }

    @Transactional
    public String unarchiveListing(String id, String userId) {
        Listing listing = (Listing) getListingById(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new RuntimeException("You can only unarchive your own listings");
        }
        if (listing.isDeleted()) {
            throw new RuntimeException("Listing has been deleted and cannot be unarchived");
        }
        if (!listing.isArchived()) {
            throw new RuntimeException("Listing is not archived — nothing to unarchive");
        }
        listing.setArchived(false);
        listing.setActive(true);
        saveListing(listing);
        return "Listing unarchived successfully";
    }

    // ====================== DISCOVERY ======================

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

        if (category == null) {
            List<Object> allListings = new ArrayList<>();
            allListings.addAll(shortTermRentalRepository.findByOwnerId(ownerId));
            allListings.addAll(longTermRentalRepository.findByOwnerId(ownerId));
            allListings.addAll(landSaleRepository.findByOwnerId(ownerId));
            allListings.addAll(houseSaleRepository.findByOwnerId(ownerId));
            allListings.addAll(leaseRepository.findByOwnerId(ownerId));
            return allListings;
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

        List<Object> all = switch (category) {
            case SHORT_TERM_RENTAL -> {
                if (city != null && !city.trim().isEmpty()) {
                    yield new ArrayList<>(shortTermRentalRepository.findByCityAndActiveTrue(city));
                } else {
                    yield new ArrayList<>(shortTermRentalRepository.findAll());
                }
            }
            case LONG_TERM_RENTAL -> new ArrayList<>(longTermRentalRepository.findAll());
            case LAND_SALE -> new ArrayList<>(landSaleRepository.findAll());
            case HOUSE_SALE -> new ArrayList<>(houseSaleRepository.findAll());
            case LEASE -> new ArrayList<>(leaseRepository.findAll());
        };

        return all.stream()
                .filter(obj -> obj instanceof Listing l && isListingVisible(l))
                .toList();
    }

    public List<?> searchListings(String query, ListingCategory category, Double minPrice, Double maxPrice, String city) {
        List<Object> results = new ArrayList<>();

        List<?> candidates;
        if (category != null) {
            candidates = getListingsByCategory(category, city);
        } else {
            List<Object> all = new ArrayList<>();
            all.addAll(shortTermRentalRepository.findAll());
            all.addAll(longTermRentalRepository.findAll());
            all.addAll(landSaleRepository.findAll());
            all.addAll(houseSaleRepository.findAll());
            all.addAll(leaseRepository.findAll());
            candidates = all;
        }

        for (Object obj : candidates) {
            if (obj instanceof Listing listing && isListingVisible(listing)) {
                results.add(listing);
            }
        }
        return results;
    }

    private boolean isListingVisible(Listing listing) {
        if (listing.isDeleted()) return false;
        if (!listing.isActive() || listing.isArchived()) return false;
        if (listing.getStatus() == ListingStatus.REJECTED) return false;
        if (listing.getStatus() == ListingStatus.ACTIVE) return true;

        return listing.isPromoted() && listing.getPromotionEndDate() != null
                && listing.getPromotionEndDate().isAfter(LocalDateTime.now());
    }

    // ====================== MY LISTINGS ======================

    public List<MyListingResponse> getMyListings(String ownerId, ListingStatus statusFilter) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return List.of();
        }

        List<?> rawListings = getListingsByOwner(ownerId, null);
        List<MyListingResponse> result = new ArrayList<>();

        for (Object obj : rawListings) {
            if (obj instanceof Listing listing) {
                MyListingResponse response = mapToMyListingResponse(listing);
                if (statusFilter == null || response.getStatus() == statusFilter) {
                    result.add(response);
                }
            }
        }
        return result;
    }

    private MyListingResponse mapToMyListingResponse(Listing listing) {
        Integer daysRemaining = null;
        if (listing.isPromoted() && listing.getPromotionEndDate() != null) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), listing.getPromotionEndDate());
            daysRemaining = (days > 0) ? (int) days : 0;
        }

        MyListingResponse resp = new MyListingResponse();
        resp.setId(listing.getId());
        resp.setTitle(listing.getTitle());
        resp.setDescription(listing.getDescription());
        resp.setCategory(listing.getCategory());
        resp.setStatus(listing.getStatus());
        resp.setPrice(listing.getPrice());
        resp.setCurrency(listing.getCurrency());
        resp.setCity(listing.getCity());
        resp.setMainImageUrl(listing.getMainImageUrl());
        resp.setImageUrls(listing.getImageUrls());
        resp.setPromoted(listing.isPromoted());
        resp.setPromotionEndDate(listing.getPromotionEndDate());
        resp.setDaysRemaining(daysRemaining);
        resp.setCreatedAt(listing.getCreatedAt());
        resp.setUpdatedAt(listing.getUpdatedAt());

        return resp;
    }
}