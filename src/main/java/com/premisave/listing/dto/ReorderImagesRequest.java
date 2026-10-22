package com.premisave.listing.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReorderImagesRequest(@NotEmpty List<String> fileIds) {
}
