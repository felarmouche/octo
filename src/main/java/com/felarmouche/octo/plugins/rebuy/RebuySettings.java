package com.felarmouche.octo.plugins.rebuy;

import java.util.Objects;

/**
 * @param referenceProductUrl the Rebuy product page Listings are compared against
 * @param minMarginInCents    the profit a Listing must leave at least to be BUY
 * @param minAiConfidence     AI assessments below this (0..1) give UNSURE
 * @param maxPhotos           the max number of photos sent to the AI
 */
public record RebuySettings(String referenceProductUrl, int minMarginInCents, double minAiConfidence, int maxPhotos) {
    public RebuySettings {
        Objects.requireNonNull(referenceProductUrl, "referenceProductUrl");
        if (referenceProductUrl.isBlank()) throw new IllegalArgumentException("referenceProductUrl must not be blank");
        if (minMarginInCents < 0) throw new IllegalArgumentException("minMarginInCents must not be negative");
        if (minAiConfidence < 0 || minAiConfidence > 1)
            throw new IllegalArgumentException("minAiConfidence must lie within 0..1");
        if (maxPhotos < 1) throw new IllegalArgumentException("maxPhotos must be at least 1");
    }
}
