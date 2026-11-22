package com.felarmouche.octo.core.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One offer for sale on a marketplace, as Octo captured it at a point in time.
 * Immutable: Plugins never change a Listing; Evaluators return Verdicts instead.
 *
 * @param priceInCents       absent when the seller gave no price
 * @param marketplaceDetails raw fields in the marketplace's own wording
 * @param attributes         the attributes of its Listing Type, absent for generic
 */
public record Listing(
        String marketplace,
        String externalId,
        String title,
        String description,
        String link,
        List<String> images,
        Instant foundAt,
        Optional<Integer> priceInCents,
        Map<String, String> marketplaceDetails,
        Optional<ListingAttributes> attributes) {

    public Listing {
        Objects.requireNonNull(marketplace, "marketplace");
        Objects.requireNonNull(foundAt, "foundAt");
        title = title == null ? "" : title;
        description = description == null ? "" : description;
        images = images == null ? List.of() : List.copyOf(images);
        priceInCents = priceInCents == null ? Optional.empty() : priceInCents;
        marketplaceDetails = marketplaceDetails == null ? Map.of() : Map.copyOf(marketplaceDetails);
        attributes = attributes == null ? Optional.empty() : attributes;
    }

    public Fingerprint fingerprint() {
        return Fingerprint.of(marketplace, title, description, priceInCents);
    }

    public Listing withAttributes(ListingAttributes newAttributes) {
        return new Listing(marketplace, externalId, title, description, link, images, foundAt,
                priceInCents, marketplaceDetails, Optional.ofNullable(newAttributes));
    }
}
