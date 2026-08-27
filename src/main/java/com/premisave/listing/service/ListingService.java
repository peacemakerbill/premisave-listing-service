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
import com.premisave.listing.exception.AuthenticationFailedException;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.repository.*;
import com.premisave.listing.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.regex.Pattern;

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
    private final JwtService jwtService; // for role extraction
    private final MongoTemplate mongoTemplate;

    @Value("${ad.promotion.daily-rate:2.99}")
    private BigDecimal dailyRate;

    // ====================== CREATE ======================

    /**
     * NOTE: this used to wrap its entire body in try/catch(Exception e),
     * log the exception with a trailing throwable (printing a full stack
     * trace on every failure, including routine ones like "auth-service is
     * down"), and re-wrap EVERY failure into a generic RuntimeException
     * with a "Failed to create listing: " prefix. That discarded the real
     * exception type before it ever reached GlobalExceptionHandler — so an
     * auth failure, a permissions failure, and a genuine bug all looked
     * identical: a 400 with a stack trace. Removed entirely: every
     * exception this method can throw (AuthenticationFailedException,
     * AccessDeniedException, IllegalArgumentException, a Mongo hiccup) now
     * has its own correct, quietly-logged handler in GlobalExceptionHandler
     * — there's nothing left for a local catch to usefully add.
     */
    @Transactional
    public ListingResponse createListing(ListingRequest request, String authorizationHeader) {
        UserSummaryResponse user = authServiceClient.getCurrentUser(authorizationHeader);
        if (user == null || user.getId() == null) {
            throw new AuthenticationFailedException("User authentication failed. Please login again.");
        }

        // ====================== ROLE CHECK ======================
        // Extract the raw token (strip "Bearer " prefix if present)
        String token = authorizationHeader;
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        String role = jwtService.extractRole(token);
        if (!"HOME_OWNER".equals(role)) {
            // This is a permissions failure, not a generic domain error —
            // AccessDeniedException gets a clean 403 from
            // GlobalExceptionHandler instead of a 400.
            throw new AccessDeniedException(
                "Access denied: only HOME_OWNER accounts can create listings. " +
                "Your current role is: " + (role != null ? role : "unknown")
            );
        }
        // ========================================================

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

        // Listings start as PENDING and inactive — they only become visible
        // once the owner promotes them and the payment is confirmed.
        listing.setStatus(ListingStatus.PENDING);
        listing.setActive(false);

        listing = saveListing(listing);

        log.info("New listing created (PENDING) by HOME_OWNER={}: listingId={}", user.getId(), listing.getId());

        return new ListingResponse("Listing created successfully. Promote it to make it visible to customers.", listing.getId(), listing.getTitle(), true);
    }

    private Listing createSpecificListing(ListingRequest request) {
        return switch (request.getCategory()) {
            case SHORT_TERM_RENTAL -> createShortTermRental(request);
            case LONG_TERM_RENTAL  -> createLongTermRental(request);
            case LAND_SALE         -> createLandSale(request);
            case HOUSE_SALE        -> createHouseSale(request);
            case LEASE             -> createLease(request);
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

    /** Same fix as createListing — try/catch(Exception) that logged with a
     *  trailing throwable and re-wrapped everything into a generic
     *  RuntimeException removed; ownership check now throws
     *  AccessDeniedException (403) instead of a generic RuntimeException
     *  (400). */
    @Transactional
    public ListingResponse updateListing(String id, ListingUpdateRequest request, String userId) {
        Listing existing = (Listing) getListingById(id);
        if (!existing.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("You can only update your own listings");
        }

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
    }

    // ====================== SAVE ======================

    public Listing saveListing(Listing listing) {
        return switch (listing) {
            case ShortTermRental st -> shortTermRentalRepository.save(st);
            case LongTermRental  lt -> longTermRentalRepository.save(lt);
            case LandSale        ls -> landSaleRepository.save(ls);
            case HouseSale       hs -> houseSaleRepository.save(hs);
            case Lease           l  -> leaseRepository.save(l);
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
            // Message only — no trailing throwable, so no stack trace.
            log.error("Cloudinary upload failed for file {}: {} — {}",
                    file.getOriginalFilename(), e.getClass().getSimpleName(), e.getMessage());
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

    /** Not-found now maps to a proper 404 via NotFoundException, instead of
     *  a generic RuntimeException (400) — consistent with AdminService's
     *  identical lookup. */
    public Object getListingById(String id) {
        return shortTermRentalRepository.findById(id)
                .map(l -> (Object) l)
                .or(() -> longTermRentalRepository.findById(id).map(l -> (Object) l))
                .or(() -> landSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> houseSaleRepository.findById(id).map(l -> (Object) l))
                .or(() -> leaseRepository.findById(id).map(l -> (Object) l))
                .orElseThrow(() -> new NotFoundException("Listing not found"));
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

    /** Ownership checks below now throw AccessDeniedException (403)
     *  instead of a generic RuntimeException (400) — state-conflict checks
     *  ("already deleted", "already archived") stay as RuntimeException
     *  (400), consistent with the equivalent checks in AdminService. */
    @Transactional
    public String deleteListing(String id, String userId) {
        Listing listing = (Listing) getListingById(id);
        if (!listing.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("You can only delete your own listings");
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
            throw new AccessDeniedException("You can only archive your own listings");
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
            throw new AccessDeniedException("You can only unarchive your own listings");
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
            return shortTermRentalRepository.findAll().stream()
                    .filter(l -> l.isPromoted()
                            && l.getPromotionEndDate() != null
                            && l.getPromotionEndDate().isAfter(LocalDateTime.now()))
                    .toList();
        }
        return shortTermRentalRepository.findByCityAndActiveTrue(city).stream()
                .filter(l -> l.isPromoted()
                        && l.getPromotionEndDate() != null
                        && l.getPromotionEndDate().isAfter(LocalDateTime.now()))
                .toList();
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
            case LONG_TERM_RENTAL  -> longTermRentalRepository.findByOwnerId(ownerId);
            case LAND_SALE         -> landSaleRepository.findByOwnerId(ownerId);
            case HOUSE_SALE        -> houseSaleRepository.findByOwnerId(ownerId);
            case LEASE             -> leaseRepository.findByOwnerId(ownerId);
        };
    }

    /**
     * Builds one Mongo Query with whichever of city / price-range /
     * free-text filters were supplied, applied consistently across every
     * listing category. Previously, city filtering only worked at the
     * query level for SHORT_TERM_RENTAL — every other category loaded its
     * entire collection via findAll() and relied on an in-memory loop to
     * filter by city (the same inconsistency the earlier LocationService
     * fix addressed for a different endpoint). This is shared by both
     * searchListings and getMyListings below.
     */
    private Query buildListingQuery(String city, Double minPrice, Double maxPrice, String textQuery) {
        Query query = new Query();

        if (city != null && !city.isBlank()) {
            query.addCriteria(Criteria.where("city").regex(Pattern.quote(city.trim()), "i"));
        }

        if (minPrice != null && maxPrice != null) {
            // Pass the raw Double directly rather than wrapping in
            // BigDecimal.valueOf(...): Spring Data MongoDB's query-time
            // conversion of a manually-built Criteria value doesn't
            // reliably go through the same BigDecimal->Decimal128
            // conversion path that document writes do, which was silently
            // breaking range comparisons against the BigDecimal-typed
            // price field. MongoDB itself compares numeric BSON types
            // (double, decimal128, int, etc.) against each other
            // correctly regardless of exact subtype, so passing a plain
            // number sidesteps the conversion question entirely instead of
            // depending on it working correctly.
            query.addCriteria(Criteria.where("price").gte(minPrice).lte(maxPrice));
        } else if (minPrice != null) {
            query.addCriteria(Criteria.where("price").gte(minPrice));
        } else if (maxPrice != null) {
            query.addCriteria(Criteria.where("price").lte(maxPrice));
        }

        if (textQuery != null && !textQuery.isBlank()) {
            String pattern = Pattern.quote(textQuery.trim());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("title").regex(pattern, "i"),
                    Criteria.where("description").regex(pattern, "i")
            ));
        }

        return query;
    }

    /** Runs the given query against one category's collection, or all five
     *  if category is null — one database round trip per category
     *  involved, instead of loading every category into memory and
     *  filtering in the JVM. */
    private List<Object> findAcrossCategories(ListingCategory category, Query query) {
        if (category != null) {
            return findByCategory(category, query);
        }
        List<Object> results = new ArrayList<>();
        results.addAll(mongoTemplate.find(query, ShortTermRental.class));
        results.addAll(mongoTemplate.find(query, LongTermRental.class));
        results.addAll(mongoTemplate.find(query, LandSale.class));
        results.addAll(mongoTemplate.find(query, HouseSale.class));
        results.addAll(mongoTemplate.find(query, Lease.class));
        return results;
    }

    private List<Object> findByCategory(ListingCategory category, Query query) {
        return switch (category) {
            case SHORT_TERM_RENTAL -> new ArrayList<>(mongoTemplate.find(query, ShortTermRental.class));
            case LONG_TERM_RENTAL  -> new ArrayList<>(mongoTemplate.find(query, LongTermRental.class));
            case LAND_SALE         -> new ArrayList<>(mongoTemplate.find(query, LandSale.class));
            case HOUSE_SALE        -> new ArrayList<>(mongoTemplate.find(query, HouseSale.class));
            case LEASE             -> new ArrayList<>(mongoTemplate.find(query, Lease.class));
        };
    }

    /**
     * Public listing search. Filters by any combination of free-text query
     * (title/description, case-insensitive substring), category, city, and
     * price range. This replaces the old, separate "get listings by
     * category" endpoint entirely — every one of its use cases is just
     * this method with category set and everything else left blank, so
     * keeping both was redundant.
     *
     * Always restricted to publicly visible listings (not deleted, not
     * archived, not rejected, actively promoted) — the same rule
     * isListingVisible used to apply after loading candidates into memory,
     * now enforced at the database query level instead.
     */
    public List<?> searchListings(String query, ListingCategory category, Double minPrice, Double maxPrice, String city) {
        Query mongoQuery = buildListingQuery(city, minPrice, maxPrice, query);
        mongoQuery.addCriteria(Criteria.where("deleted").is(false));
        mongoQuery.addCriteria(Criteria.where("archived").is(false));
        mongoQuery.addCriteria(Criteria.where("status").ne(ListingStatus.REJECTED));
        mongoQuery.addCriteria(Criteria.where("isPromoted").is(true));
        mongoQuery.addCriteria(Criteria.where("promotionEndDate").gt(LocalDateTime.now()));

        return findAcrossCategories(category, mongoQuery);
    }

    // ====================== MY LISTINGS ======================

    /**
     * Filterable by everything searchListings supports (category, city,
     * price range, free-text query), scoped to the caller's own listings
     * via ownerId — plus an exact status filter, which searchListings
     * doesn't have since it only ever shows visible/promoted listings
     * regardless of status. "My listings" needs to show PENDING/REJECTED/
     * ACTIVE ones too, since checking on unpromoted listings is the whole
     * point of this endpoint.
     */
    public List<MyListingResponse> getMyListings(
            String ownerId,
            ListingStatus statusFilter,
            ListingCategory category,
            String city,
            Double minPrice,
            Double maxPrice,
            String query) {
        if (ownerId == null || ownerId.trim().isEmpty()) {
            return List.of();
        }

        Query mongoQuery = buildListingQuery(city, minPrice, maxPrice, query);
        mongoQuery.addCriteria(Criteria.where("ownerId").is(ownerId));
        if (statusFilter != null) {
            mongoQuery.addCriteria(Criteria.where("status").is(statusFilter));
        }

        List<Object> rawListings = findAcrossCategories(category, mongoQuery);
        List<MyListingResponse> result = new ArrayList<>();

        for (Object obj : rawListings) {
            if (obj instanceof Listing listing) {
                result.add(mapToMyListingResponse(listing));
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