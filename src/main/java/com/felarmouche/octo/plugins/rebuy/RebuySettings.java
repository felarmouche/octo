package com.felarmouche.octo.plugins.rebuy;

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
public record RebuySettings(@NotBlank String referenceProductUrl,
                            @PositiveOrZero int minMarginInCents,
                            @DecimalMin("0") @DecimalMax("1") double minAiConfidence,
                            @Min(1) int maxPhotos) {
}
