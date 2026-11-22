package com.felarmouche.octo.core.listingtype;

import com.felarmouche.octo.core.domain.Listing;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Optional;

/**
 * An optional price range of a Listing Type Filter. Either bound may be null (open).
 * A Listing without a price passes.
 */
public record PriceRange(@Schema(title = "Mindestpreis", format = "money-cents") @PositiveOrZero Integer minInCents,
                         @Schema(title = "Höchstpreis", format = "money-cents") @PositiveOrZero Integer maxInCents) {

    public static final PriceRange ANY = new PriceRange(null, null);

    public PriceRange {
        if (minInCents != null && maxInCents != null && minInCents > maxInCents)
            throw new IllegalArgumentException("minInCents must not be above maxInCents");
    }

    /** The reason when the Listing's price is outside the range, e.g. "Preis 300 € > 250 €". */
    public Optional<String> reject(Listing listing) {
        if (listing.priceInCents().isEmpty()) return Optional.empty();
        int price = listing.priceInCents().get();
        if (minInCents != null && price < minInCents)
            return Optional.of("Preis " + euros(price) + " < " + euros(minInCents));
        if (maxInCents != null && price > maxInCents)
            return Optional.of("Preis " + euros(price) + " > " + euros(maxInCents));
        return Optional.empty();
    }

    private static String euros(int cents) {
        return cents % 100 == 0 ? "%d €".formatted(cents / 100) : "%d,%02d €".formatted(cents / 100, cents % 100);
    }
}
