package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.Listing;
import com.felarmouche.octo.core.shared.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class BaseFilter extends Plugin implements FilterI {

    public BaseFilter(String id) {
        super(id);
    }

    @Override
    public List<Listing> filter(List<Listing> found) {
        var dedupList = removeDuplicates(found);
        return customFilter(dedupList);
    }

    abstract public List<Listing> customFilter(Set<Listing> listings);

    private Map<Fingerprint, Listing> last = new HashMap<>();

    protected Set<Listing> removeDuplicates(List<Listing> found) {
        Set<Listing> filtered = found.stream()
                .filter(it -> !last.containsKey(it.getFingerprint()))
                .collect(Collectors.toSet());

        updateLast(filtered);
        return filtered;
    }

    private void updateLast(Set<Listing> listings){
        last = listings.stream()
                .collect(Collectors.toMap(Listing::getFingerprint, Function.identity()));
    }
}
