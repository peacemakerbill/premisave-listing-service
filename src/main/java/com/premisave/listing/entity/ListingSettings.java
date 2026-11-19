package com.premisave.listing.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * The marketplace settings an admin can change. There is one row, with the id "global".
 * With [reviewRequired] off (the default) a new listing is approved the moment it is created, and the only thing that makes
 * it visible to clients is a promotion. With it on, an admin has to approve a listing before it can be promoted.
 */
@Data
@Document(collection = "listing_settings")
public class ListingSettings {

    public static final String ID = "global";

    @Id
    private String id = ID;

    private boolean reviewRequired = false;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
