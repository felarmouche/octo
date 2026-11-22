package com.felarmouche.octo.plugins.kleinanzeigen;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * @param searchUrl   Kleinanzeigen's own search URL, with price range, location and keywords already in it
 * @param maxListings the max number of Listings per run
 */
public record KleinanzeigenSettings(
        @Schema(title = "Kleinanzeigen-Suchlink",
                description = "Die Adresse einer Suche auf Kleinanzeigen, mit Preis, Ort und Suchwörtern")
        @NotBlank String searchUrl,
        @Schema(title = "Max. Anzeigen pro Lauf", defaultValue = "20")
        @Min(1) int maxListings) {
}
