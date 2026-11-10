package com.felarmouche.octo.core.evaluator;

import com.felarmouche.octo.application.SearchSettings;
import com.felarmouche.octo.core.shared.listing.Listing;

import java.util.List;

public interface EvaluatorI {
    public void evaluate(List<Listing> listings, SearchSettings settings);
}
