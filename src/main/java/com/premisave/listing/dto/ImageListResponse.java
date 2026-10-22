package com.premisave.listing.dto;

import java.util.List;

public record ImageListResponse(String mainImageUrl, List<String> imageUrls) {
}
