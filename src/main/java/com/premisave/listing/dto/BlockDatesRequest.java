package com.premisave.listing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BlockDatesRequest {

    /** First night to block. */
    @NotNull(message = "from is required")
    private LocalDate from;

    /** The date the block ends on; this night itself stays open (same rule as a check-out date). */
    @NotNull(message = "to is required")
    private LocalDate to;
}
