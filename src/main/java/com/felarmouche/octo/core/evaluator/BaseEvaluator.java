package com.felarmouche.octo.core.evaluator;

import com.felarmouche.octo.application.SearchSettings;
import com.felarmouche.octo.core.shared.listing.Listing;
import com.felarmouche.octo.core.shared.Plugin;

import java.util.List;

public abstract class BaseEvaluator extends Plugin implements EvaluatorI {

    public BaseEvaluator(String id) {
        super(id);
    }

    @Override
    public abstract void evaluate(List<Listing> listings, SearchSettings settings);
}
