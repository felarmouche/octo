package com.felarmouche.octo.core.shared.listing;

import lombok.Getter;

@Getter
public enum SupportedListings {
    Phone("phone");

    private final String label;

    SupportedListings(String label) {
        this.label = label;
    }

    public static SupportedListings get(String type) {
        for (SupportedListings listing : values()) {
            if (listing.label.equalsIgnoreCase(type)) {
                return listing;
            }
        }
        throw new IllegalArgumentException("Unsupported listing type: " + type);
    }
}
