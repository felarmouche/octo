package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Fingerprint;

import java.util.Collection;
import java.util.List;

/**
 * What a Search keeps between runs: the Fingerprints it has seen and its Listing History.
 * Seen is separate from history: a Fingerprint stays remembered after its Listing left the history.
 */
public interface SearchMemory {

    boolean hasSeen(String searchId, Fingerprint fingerprint);

    void remember(String searchId, Collection<Fingerprint> fingerprints);

    void record(String searchId, List<JudgedListing> judged);

    List<JudgedListing> history(String searchId);
}
