package com.felarmouche.octo.core.plugin;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.listingtype.ListingType;

import java.util.List;
import java.util.Objects;

/** A Plugin that turns the ads of one marketplace into Listings of the Search's Listing Type. */
public interface Finder<S extends Record> extends Plugin<S> {

    Result find(S settings, ListingType listingType);

    /**
     * The Listings found, plus problems that only skipped part of the work
     * (e.g. one unloadable ad detail page). A failure of the whole Finder is a {@link PluginException}.
     */
    record Result(List<Listing> listings, List<String> problems) {
        public Result {
            listings = List.copyOf(Objects.requireNonNull(listings, "listings"));
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        public static Result of(List<Listing> listings) {
            return new Result(listings, List.of());
        }
    }
}
