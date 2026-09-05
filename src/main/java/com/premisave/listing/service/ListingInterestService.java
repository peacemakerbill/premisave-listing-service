package com.premisave.listing.service;

import com.premisave.listing.client.AuthServiceClient;
import com.premisave.listing.dto.ExpressInterestRequest;
import com.premisave.listing.dto.InterestResponse;
import com.premisave.listing.dto.auth_service.UserSummaryResponse;
import com.premisave.listing.entity.Listing;
import com.premisave.listing.entity.ListingInterest;
import com.premisave.listing.exception.AuthenticationFailedException;
import com.premisave.listing.exception.NotFoundException;
import com.premisave.listing.repository.ListingInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Lead capture for long-term rentals, leases, and sales — no money or
 * booking flow involved, just recording a customer's interest so the
 * owner can reach out.
 *
 * Deliberately stores almost nothing about people: only stable IDs
 * (customerId, customerEmail) live on ListingInterest. Every response
 * embeds a full UserSummaryResponse fetched live from auth-service via
 * AuthServiceClient — never a stored snapshot. Only the OTHER party
 * relevant to whoever's viewing is included (see InterestResponse) —
 * the caller already knows their own details. If auth-service is
 * unreachable, AuthServiceClientFallbackFactory throws
 * AuthServiceUnavailableException, which propagates straight through
 * every method here (nothing catches it) to GlobalExceptionHandler,
 * giving the caller a clean 503 instead of a stale or missing response.
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
        UserSummaryResponse customer = authServiceClient.getCurrentUser(authHeader);
        if (customer == null || !customer.getId().equals(customerId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

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
        interest.setCustomerEmail(customer.getEmail());
        interest.setMessage(request.getMessage());
        interest = interestRepository.save(interest);

        UserSummaryResponse owner = authServiceClient.getUserSummary(listing.getOwnerId(), authHeader);
        emailService.sendInterestExpressedEmails(
                customer.getEmail(), customer.getFullName(),
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                listing.getTitle());

        log.info("Interest expressed: listing={}, customer={}", listing.getId(), customerId);

        // Caller is the customer — they know their own details, so the
        // useful thing to hand back is who they'd actually be dealing
        // with (the owner), not an echo of their own profile.
        return toResponse(interest, null, owner);
    }

    @Transactional
    public void cancelInterest(String interestId, String customerId, String authHeader) {
        ListingInterest interest = interestRepository.findById(interestId)
                .orElseThrow(() -> new NotFoundException("Interest not found"));

        if (!interest.getCustomerId().equals(customerId)) {
            throw new AccessDeniedException("You can only cancel your own expressed interest.");
        }

        // Fetched live for the email greeting/name — not stored, so this is
        // the only way to get the customer's current display name.
        UserSummaryResponse customer = authServiceClient.getUserSummary(interest.getCustomerId(), authHeader);
        UserSummaryResponse owner = authServiceClient.getUserSummary(interest.getListingOwnerId(), authHeader);

        interestRepository.delete(interest);

        emailService.sendInterestCancelledEmails(
                interest.getCustomerEmail(), customer != null ? customer.getFullName() : null,
                owner != null ? owner.getEmail() : null, owner != null ? owner.getFullName() : null,
                interest.getListingTitle());

        log.info("Interest cancelled and deleted: listing={}, customer={}", interest.getListingId(), customerId);
    }

    /**
     * The caller's own expressed interests, viewed as the customer — each
     * item embeds the LISTING OWNER's details (who they'd actually be
     * dealing with), never the caller's own. Owners vary per item (each
     * interest can point to a different listing), so they're fetched per
     * distinct ownerId within the page (deduplicated via a page-scoped
     * cache) rather than once per item.
     */
    public Page<InterestResponse> getMyInterests(String customerId, String authHeader, Pageable pageable) {
        UserSummaryResponse customer = authServiceClient.getCurrentUser(authHeader);
        if (customer == null || !customer.getId().equals(customerId)) {
            throw new AuthenticationFailedException("User authentication failed. Please log in again.");
        }

        Map<String, UserSummaryResponse> ownerCache = new HashMap<>();
        return interestRepository.findByCustomerId(customerId, pageable)
                .map(interest -> {
                    UserSummaryResponse owner = ownerCache.computeIfAbsent(
                            interest.getListingOwnerId(),
                            id -> authServiceClient.getUserSummary(id, authHeader));
                    return toResponse(interest, null, owner);
                });
    }

    /**
     * The owner's received leads across all their listings — each item
     * embeds the CUSTOMER's details (the lead's contact info), never the
     * owner's own. Unlike getMyInterests, each item can belong to a
     * DIFFERENT customer, so this fetches per distinct customer within
     * the page — still one auth-service call per unique customer in the
     * worst case, since there's no batch-lookup-by-ids endpoint.
     */
    public Page<InterestResponse> getInterestsForMyListings(String ownerId, String authHeader, Pageable pageable) {
        Map<String, UserSummaryResponse> customerCache = new HashMap<>();
        return interestRepository.findByListingOwnerId(ownerId, pageable)
                .map(interest -> {
                    UserSummaryResponse customer = customerCache.computeIfAbsent(
                            interest.getCustomerId(),
                            id -> authServiceClient.getUserSummary(id, authHeader));
                    return toResponse(interest, customer, null);
                });
    }

    private InterestResponse toResponse(ListingInterest interest, UserSummaryResponse customer, UserSummaryResponse owner) {
        return new InterestResponse(
                interest.getId(),
                interest.getListingId(),
                interest.getListingTitle(),
                interest.getMessage(),
                interest.getCreatedAt(),
                customer,
                owner
        );
    }
}