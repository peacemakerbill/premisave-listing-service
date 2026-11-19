package com.premisave.listing.service;

import com.premisave.listing.entity.ListingSettings;
import com.premisave.listing.repository.ListingSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/** Reads and changes the marketplace settings. A missing row means every setting is at its default. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListingSettingsService {

    private final ListingSettingsRepository repository;

    public ListingSettings get() {
        return repository.findById(ListingSettings.ID).orElseGet(ListingSettings::new);
    }

    /** True when an admin has to approve new listings before they can be promoted. */
    public boolean reviewRequired() {
        return get().isReviewRequired();
    }

    public ListingSettings setReviewRequired(boolean required, String adminId) {
        ListingSettings settings = get();
        settings.setReviewRequired(required);
        settings.setUpdatedBy(adminId);
        settings.setUpdatedAt(LocalDateTime.now());
        ListingSettings saved = repository.save(settings);
        log.info("Listing review {} by {}", required ? "turned on" : "turned off", adminId);
        return saved;
    }
}
