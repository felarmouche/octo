package com.felarmouche.octo.core.evaluator;

import com.felarmouche.octo.core.shared.Listing;
import com.felarmouche.octo.core.shared.Plugin;

import java.util.List;
import java.util.Set;

public abstract class BaseEvaluator extends Plugin implements EvaluatorI {

    public BaseEvaluator(String id) {
        super(id);
    }

    @Override
    public abstract void evaluate(List<Listing> listings);
}
