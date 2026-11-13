package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.Plugin;

import java.util.List;
import java.util.Map;

public class BaseFilter extends Plugin implements FilterI {

    private final static String PLUGIN_ID= "base-filter";

    public BaseFilter() {
        super(PLUGIN_ID);
    }

    @Override
    public List<Listing> filter(List<Listing> found, Map<Fingerprint, Listing> last) {
        var dedupList = removeDuplicates(found, last);
        return customFilter(dedupList);
    }

    @Override
    public List<Listing> customFilter(List<Listing> listings){
        return listings;
    }

    protected List<Listing> removeDuplicates(List<Listing> found, Map<Fingerprint, Listing> last) {
        return found.stream()
                .filter(it -> !last.containsKey(it.getFingerprint()))
                        .toList();
    }

}
