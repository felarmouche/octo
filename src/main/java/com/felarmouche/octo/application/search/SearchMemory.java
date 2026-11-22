package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Fingerprint;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What a Search keeps between runs: the Fingerprints it has seen, its Listing History and its last run status.
 * Seen is separate from history: a Fingerprint stays remembered after its Listing left the history.
 */
public interface SearchMemory {

    boolean hasSeen(String searchId, Fingerprint fingerprint);

    void remember(String searchId, Collection<Fingerprint> fingerprints);

    /** Adds the judged Listings to the Listing History, then applies the retention rule. */
    void record(String searchId, List<JudgedListing> judged, Retention retention);

    /** The Listing History in the order it was recorded. */
    List<JudgedListing> history(String searchId);

    /** Replaces the Search's last run status. */
    void saveStatus(String searchId, RunStatus status);

    /** Empty until the Search has run once. */
    Optional<RunStatus> lastStatus(String searchId);
}
