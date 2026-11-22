package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;

import java.time.Instant;

/** One entry of the Listing History: a judged Listing with its id and the time it was recorded. */
public record RecordedListing(long id, Instant recordedAt, Listing listing, Verdict verdict) {
}
