package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;

/** A Listing with the Verdict the run gave it, as recorded in the Listing History. */
public record JudgedListing(Listing listing, Verdict verdict) {
}
