package com.felarmouche.octo.core.listingtype;

import com.felarmouche.octo.core.domain.Listing;

import java.util.Optional;

/**
 * An optional price range of a Listing Type Filter. Either bound may be null (open).
 * A Listing without a price passes.
 */
public record PriceRange(Integer minInCents, Integer maxInCents) {

    public static final PriceRange ANY = new PriceRange(null, null);

    public PriceRange {
        if (minInCents != null && minInCents < 0) throw new IllegalArgumentException("minInCents must not be negative");
        if (maxInCents != null && maxInCents < 0) throw new IllegalArgumentException("maxInCents must not be negative");
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
