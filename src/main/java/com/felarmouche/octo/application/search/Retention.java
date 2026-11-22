package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Decision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.Optional;
import java.util.Set;

/**
 * The retention rule of a Search's Listing History, applied after each run:
 * keep only the given Decisions, then the newest N Listings.
 *
 * @param decisions the Decisions to keep; empty or null keeps every Decision
 * @param newest    how many Listings to keep; empty keeps them all
 */
public record Retention(
        @Schema(title = "Entscheidungen behalten", description = "Keine Auswahl: alle Entscheidungen") Set<Decision> decisions,
        @Schema(title = "Neueste behalten", description = "Leer: alle behalten") Optional<@Min(1) Integer> newest) {

    public static final Retention KEEP_ALL = new Retention(Set.of(), Optional.empty());
    /** The newest 500 Listings, whatever their Decision. */
    public static final Retention DEFAULT = new Retention(Set.of(), Optional.of(500));

    public Retention {
        decisions = decisions == null ? Set.of() : Set.copyOf(decisions);
        newest = newest == null ? Optional.empty() : newest;
        if (newest.isPresent() && newest.get() < 1)
            throw new IllegalArgumentException("retention must keep at least one Listing: " + newest.get());
    }
}
