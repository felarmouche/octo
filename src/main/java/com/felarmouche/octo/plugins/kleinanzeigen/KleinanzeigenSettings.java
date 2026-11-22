package com.felarmouche.octo.plugins.kleinanzeigen;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * @param searchUrl   Kleinanzeigen's own search URL, with price range, location and keywords already in it
 * @param maxListings the max number of Listings per run
 */
public record KleinanzeigenSettings(@NotBlank String searchUrl, @Min(1) int maxListings) {
}
