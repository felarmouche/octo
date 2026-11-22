package com.felarmouche.octo.plugins.rebuy;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * @param referenceProductUrl the Rebuy product page Listings are compared against
 * @param minMarginInCents    the profit a Listing must leave at least to be BUY
 * @param minAiConfidence     AI assessments below this (0..1) give UNSURE
 * @param maxPhotos           the max number of photos sent to the AI
 */
public record RebuySettings(
        @Schema(title = "Rebuy-Produktseite", description = "Das Referenzprodukt, mit dem Anzeigen verglichen werden")
        @NotBlank String referenceProductUrl,
        @Schema(title = "Mindestgewinn (Cent)", defaultValue = "2000")
        @PositiveOrZero int minMarginInCents,
        @Schema(title = "Mindestsicherheit der KI (0–1)", description = "Darunter wird die Anzeige UNSURE",
                defaultValue = "0.7")
        @DecimalMin("0") @DecimalMax("1") double minAiConfidence,
        @Schema(title = "Max. Fotos für die KI", defaultValue = "4")
        @Min(1) int maxPhotos) {
}
