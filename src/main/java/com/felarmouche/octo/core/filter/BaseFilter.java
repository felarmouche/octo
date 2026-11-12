package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.Plugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BaseFilter extends Plugin implements FilterI {

    private final static String PLUGIN_ID= "base-filter";

    public BaseFilter() {
        super(PLUGIN_ID);
    }

    @Override
    public List<Listing> filter(List<Listing> found) {
        var dedupList = removeDuplicates(found);
        return customFilter(dedupList);
    }


    @Override
    public List<Listing> customFilter(List<Listing> listings){
        return listings;
    };

    private Map<Fingerprint, Listing> last = new HashMap<>();

    protected List<Listing> removeDuplicates(List<Listing> found) {
        List<Listing> filtered = found.stream()
                .filter(it -> !last.containsKey(it.getFingerprint()))
                        .toList();

        updateLast(found);
        return filtered;
    }

    private void updateLast(List<Listing> listings){
        last = listings.stream()
                .collect(Collectors.toMap(Listing::getFingerprint, Function.identity()));
    }
}
