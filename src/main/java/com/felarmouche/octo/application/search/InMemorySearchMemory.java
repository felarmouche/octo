package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Fingerprint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Keeps seen Fingerprints and the Listing History in memory until persistence lands. */
public class InMemorySearchMemory implements SearchMemory {

    private final Map<String, Set<Fingerprint>> seen = new ConcurrentHashMap<>();
    private final Map<String, List<JudgedListing>> history = new ConcurrentHashMap<>();

    @Override
    public boolean hasSeen(String searchId, Fingerprint fingerprint) {
        return seen.getOrDefault(searchId, Set.of()).contains(fingerprint);
    }

    @Override
    public void remember(String searchId, Collection<Fingerprint> fingerprints) {
        seen.computeIfAbsent(searchId, id -> ConcurrentHashMap.newKeySet()).addAll(fingerprints);
    }

    @Override
    public void record(String searchId, List<JudgedListing> judged) {
        history.compute(searchId, (id, existing) -> {
            List<JudgedListing> updated = existing == null ? new ArrayList<>() : new ArrayList<>(existing);
            updated.addAll(judged);
            return List.copyOf(updated);
        });
    }

    @Override
    public List<JudgedListing> history(String searchId) {
        return history.getOrDefault(searchId, List.of());
    }
}
