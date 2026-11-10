package com.felarmouche.octo.core.finder;

import com.felarmouche.octo.core.shared.listing.Listing;

import java.util.List;

public interface FinderI {
    public <T> List<Listing> find(String convertTo);
}
