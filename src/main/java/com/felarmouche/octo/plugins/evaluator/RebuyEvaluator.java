package com.felarmouche.octo.plugins.evaluator;

import com.felarmouche.octo.core.evaluator.BaseEvaluator;
import com.felarmouche.octo.core.shared.Listing;

import java.util.List;

public class RebuyEvaluator extends BaseEvaluator {
    private final static String PLUGIN_ID = "rebuy-evaluator";
    private final RebuyCatalog productCatalog;

    public RebuyEvaluator(String productUrl) {
        super(PLUGIN_ID);
        this.productCatalog = new RebuyCatalog(productUrl);

    }

    @Override
    public void evaluate(List<Listing> listings) {
    }
}
