package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.listing.Listing;

import java.util.List;

public interface FilterI {
    public List<Listing> filter(List<Listing> found);

    public List<Listing> customFilter(List<Listing> found);

}
