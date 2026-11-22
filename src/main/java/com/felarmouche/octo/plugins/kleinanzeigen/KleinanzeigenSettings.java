package com.felarmouche.octo.plugins.kleinanzeigen;

import java.util.Objects;

/**
 * @param searchUrl   Kleinanzeigen's own search URL, with price range, location and keywords already in it
 * @param maxListings the max number of Listings per run
 */
public record KleinanzeigenSettings(String searchUrl, int maxListings) {
    public KleinanzeigenSettings {
        Objects.requireNonNull(searchUrl, "searchUrl");
        if (maxListings < 1) throw new IllegalArgumentException("maxListings must be at least 1");
    }
}
