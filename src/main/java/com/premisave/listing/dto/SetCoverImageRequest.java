package com.premisave.listing.dto;

import jakarta.validation.constraints.NotBlank;

public record SetCoverImageRequest(@NotBlank String fileId) {
}
