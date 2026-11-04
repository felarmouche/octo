package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.Listing;

import java.util.List;
import java.util.Set;

public interface FilterI {
    public List<Listing> filter(List<Listing> found);

    public List<Listing> customFilter();

}
