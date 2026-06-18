package com.premisave.listing.dto;

import com.premisave.listing.enums.ListingCategory;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ListingUpdateRequest extends ListingRequest {

    // Overrides the @NotNull category from ListingRequest — optional on update
    private ListingCategory category;
}