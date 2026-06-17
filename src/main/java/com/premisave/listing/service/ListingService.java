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

        // Set common fields (BEFORE saving)
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
        listing.setImageUrls(request.getImageUrls() != null ? 
                request.getImageUrls() : new java.util.ArrayList<>());
        listing.setStatus(ListingStatus.ACTIVE);

        // Save once
        listing = saveListing(listing);

        log.info("New listing created: {} by user {}", listing.getId(), user.getId());

        return new ListingResponse("Listing created successfully", listing.getId(), listing.getTitle(), true);
    }

    private Listing createSpecificListing(ListingRequest request) {
        return switch (request.getCategory()) {
            case SHORT_TERM_RENTAL -> {
                ShortTermRental st = new ShortTermRental();
                st.setMaxGuests(request.getMaxGuests());
                st.setBedrooms(request.getBedrooms());
                st.setBathrooms(request.getBathrooms());
                st.setHasWifi(Boolean.TRUE.equals(request.getHasWifi()));
                st.setHasKitchen(Boolean.TRUE.equals(request.getHasKitchen()));
                st.setAmenities(request.getAmenities());
                yield st;
            }
            case LONG_TERM_RENTAL -> {
                LongTermRental lt = new LongTermRental();
                lt.setMinLeaseMonths(request.getMinLeaseMonths());
                lt.setFurnished(Boolean.TRUE.equals(request.getFurnished()));
                lt.setTenantRequirements(request.getTenantRequirements());
                yield lt;
            }
            case LAND_SALE -> {
                LandSale ls = new LandSale();
                ls.setSizeInAcres(request.getSizeInAcres());
                ls.setLandUseType(request.getLandUseType());
                ls.setHasTitleDeed(Boolean.TRUE.equals(request.getHasTitleDeed()));
                yield ls;
            }
            case HOUSE_SALE -> {
                HouseSale hs = new HouseSale();
                hs.setBedrooms(request.getBedrooms());
                hs.setBathrooms(request.getBathrooms());
                hs.setFloors(request.getFloors());
                hs.setPlotSize(request.getPlotSize());
                hs.setHasGarage(Boolean.TRUE.equals(request.getHasGarage()));
                hs.setPropertyType(request.getPropertyType());
                yield hs;
            }
            case LEASE -> {
                Lease lease = new Lease();
                lease.setLeaseDurationMonths(request.getLeaseDurationMonths());
                lease.setDepositAmount(request.getDepositAmount());
                lease.setLeaseTerms(request.getLeaseTerms());
                lease.setRenewable(Boolean.TRUE.equals(request.getRenewable()));
                yield lease;
            }
        };
    }

    private Listing saveListing(Listing listing) {
        return switch (listing) {
            case ShortTermRental st -> shortTermRentalRepository.save(st);
            case LongTermRental lt -> longTermRentalRepository.save(lt);
            case LandSale ls -> landSaleRepository.save(ls);
            case HouseSale hs -> houseSaleRepository.save(hs);
            case Lease l -> leaseRepository.save(l);
            default -> throw new IllegalArgumentException("Unknown listing type");
        };
    }

    public List<ShortTermRental> getShortTermRentals(String city) {
        return shortTermRentalRepository.findByCityAndActiveTrue(city);
    }

    public List<?> getListingsByOwner(String ownerId, ListingCategory category) {
        return switch (category) {
            case SHORT_TERM_RENTAL -> shortTermRentalRepository.findByOwnerId(ownerId);
            case LONG_TERM_RENTAL -> longTermRentalRepository.findByOwnerId(ownerId);
            case LAND_SALE -> landSaleRepository.findByOwnerId(ownerId);
            case HOUSE_SALE -> houseSaleRepository.findByOwnerId(ownerId);
            case LEASE -> leaseRepository.findByOwnerId(ownerId);
        };
    }
}