package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.ExpressInterestRequest;
import com.premisave.listing.dto.InterestResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingInterest;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.repository.ListingInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lead capture for long-term rentals, leases, and sales — no money or
 * booking flow involved, just recording a customer's interest and contact
 * details so the owner can reach out.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingInterestService {

    private final ListingInterestRepository interestRepository;
    private final ListingService listingService;
    private final AuthServiceClient authServiceClient;
    private final EmailService emailService;

    @Transactional
    public InterestResponse expressInterest(ExpressInterestRequest request, String customerId, String authHeader) {
        Object rawListing = listingService.getListingById(request.getListingId());
        if (!(rawListing instanceof Listing listing)) {
            throw new NotFoundException("Listing not found");
        }

        interestRepository.findByListingIdAndCustomerId(request.getListingId(), customerId)
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("You have already expressed interest in this listing.");
                });

        ListingInterest interest = new ListingInterest();
        interest.setListingId(listing.getId());
        interest.setListingTitle(listing.getTitle());
        interest.setListingOwnerId(listing.getOwnerId());
        interest.setCustomerId(customerId);
        interest.setFullName(request.getFullName());
        interest.setEmail(request.getEmail());
        interest.setPhoneNumber(request.getPhoneNumber());
        interest.setAddress(request.getAddress());
        interest.setMessage(request.getMessage());
        interest = interestRepository.save(interest);

        UserSummaryResponse owner = authServiceClient.getUserSummary(listing.getOwnerId(), authHeader);
        emailService.sendInterestExpressedEmails(
                interest.getEmail(), interest.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                listing.getTitle());

        log.info("Interest expressed: listing={}, customer={}", listing.getId(), customerId);

        return toResponse(interest);
    }

    @Transactional
    public void cancelInterest(String interestId, String customerId, String authHeader) {
        ListingInterest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new NotFoundException("Interest not found"));

        if (!interest.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("You can only cancel your own expressed interest.");
        }

        UserSummaryResponse owner = authServiceClient.getUserSummary(interest.getListingOwnerId(), authHeader);

        interestRepository.delete(interest);

        emailService.sendInterestCancelledEmails(
                interest.getEmail(), interest.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                interest.getListingTitle());

        log.info("Interest cancelled and deleted: listing={}, customer={}", interest.getListingId(), customerId);
    }

    public Page<InterestResponse> getMyInterests(String customerId, Pageable pageable) {
        return interestRepository.findByCustomerId(customerId, pageable).map(this::toResponse);
    }

    /** The owner's received leads across all their listings. */
    public Page<InterestResponse> getInterestsForMyListings(String ownerId, Pageable pageable) {
        return interestRepository.findByListingOwnerId(ownerId, pageable).map(this::toResponse);
    }

    private InterestResponse toResponse(ListingInterest interest) {
        return new InterestResponse(
                interest.getId(),
                interest.getListingId(),
                interest.getFullName(),
                interest.getEmail(),
                interest.getPhoneNumber(),
                interest.getAddress(),
                interest.getMessage(),
                interest.getCreatedAt()
        );
    }
}