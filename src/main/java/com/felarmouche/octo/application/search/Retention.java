package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Decision;

import java.util.Optional;
import java.util.Set;

/**
 * The retention rule of a Search's Listing History, applied after each run:
 * keep only the given Decisions, then the newest N Listings.
 *
 * @param decisions the Decisions to keep; empty or null keeps every Decision
 * @param newest    how many Listings to keep; empty keeps them all
 */
public record Retention(Set<Decision> decisions, Optional<Integer> newest) {

    public static final Retention KEEP_ALL = new Retention(Set.of(), Optional.empty());

    public Retention {
        decisions = decisions == null ? Set.of() : Set.copyOf(decisions);
        newest = newest == null ? Optional.empty() : newest;
        if (newest.isPresent() && newest.get() < 1)
            throw new IllegalArgumentException("retention must keep at least one Listing: " + newest.get());
    }
}
