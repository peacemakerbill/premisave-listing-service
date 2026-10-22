package com.premisave.listing.storage;

public enum ImageSize {
    THUMB("thumb"),
    MEDIUM("medium"),
    ORIGINAL("original");

    private final String suffix;

    ImageSize(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    public static ImageSize parse(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        for (ImageSize size : values()) {
            if (size.suffix.equalsIgnoreCase(value.trim())) {
                return size;
            }
        }
        throw new IllegalArgumentException("size must be one of: thumb, medium, original.");
    }
}
