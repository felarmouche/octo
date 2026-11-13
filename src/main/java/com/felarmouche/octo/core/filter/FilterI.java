package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.Fingerprint;
import com.felarmouche.octo.core.shared.listing.Listing;

import java.util.List;
import java.util.Map;

public interface FilterI {
    public List<Listing> filter(List<Listing> found, Map<Fingerprint, Listing> last);

    public List<Listing> customFilter(List<Listing> found);

}
