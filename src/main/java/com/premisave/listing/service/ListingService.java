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
        // Validate user via Auth Service
        UserSummaryResponse user = authServiceClient.getCurrentUser(authorizationHeader);

        if (user == null) {
            throw new RuntimeException("User not authenticated");
        }

        Listing listing;

        switch (request.getCategory()) {
            case SHORT_TERM_RENTAL:
                ShortTermRental shortTerm = new ShortTermRental();
                shortTerm.setMaxGuests(request.getMaxGuests());
                shortTerm.setBedrooms(request.getBedrooms());
                shortTerm.setBathrooms(request.getBathrooms());
                shortTerm.setHasWifi(Boolean.TRUE.equals(request.getHasWifi()));
                shortTerm.setHasKitchen(Boolean.TRUE.equals(request.getHasKitchen()));
                shortTerm.setAmenities(request.getAmenities());
                listing = shortTerm;
                shortTermRentalRepository.save((ShortTermRental) listing);
                break;

            case LONG_TERM_RENTAL:
                LongTermRental longTerm = new LongTermRental();
                longTerm.setMinLeaseMonths(request.getMinLeaseMonths());
                longTerm.setFurnished(Boolean.TRUE.equals(request.getFurnished()));
                longTerm.setTenantRequirements(request.getTenantRequirements());
                listing = longTerm;
                longTermRentalRepository.save((LongTermRental) listing);
                break;

            case LAND_SALE:
                LandSale landSale = new LandSale();
                landSale.setSizeInAcres(request.getSizeInAcres());
                landSale.setLandUseType(request.getLandUseType());
                landSale.setHasTitleDeed(Boolean.TRUE.equals(request.getHasTitleDeed()));
                listing = landSale;
                landSaleRepository.save((LandSale) listing);
                break;

            case HOUSE_SALE:
                HouseSale houseSale = new HouseSale();
                houseSale.setBedrooms(request.getBedrooms());
                houseSale.setBathrooms(request.getBathrooms());
                houseSale.setFloors(request.getFloors());
                houseSale.setPlotSize(request.getPlotSize());
                houseSale.setHasGarage(Boolean.TRUE.equals(request.getHasGarage()));
                houseSale.setPropertyType(request.getPropertyType());
                listing = houseSale;
                houseSaleRepository.save((HouseSale) listing);
                break;

            case LEASE:
                Lease lease = new Lease();
                lease.setLeaseDurationMonths(request.getLeaseDurationMonths());
                lease.setDepositAmount(request.getDepositAmount());
                lease.setLeaseTerms(request.getLeaseTerms());
                lease.setRenewable(Boolean.TRUE.equals(request.getRenewable()));
                listing = lease;
                leaseRepository.save((Lease) listing);
                break;

            default:
                throw new IllegalArgumentException("Unsupported listing category: " + request.getCategory());
        }

        // Common fields
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
        listing.setImageUrls(request.getImageUrls());
        listing.setStatus(ListingStatus.ACTIVE);

        log.info("New listing created: {} by user {}", listing.getId(), user.getId());

        return new ListingResponse("Listing created successfully", listing.getId(), listing.getTitle(), false);
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