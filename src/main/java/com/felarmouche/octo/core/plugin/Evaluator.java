package com.felarmouche.octo.core.plugin;

import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;

/** A Plugin that judges one Listing that survived the filters. A Search has at most one. */
public interface Evaluator<S extends Record> extends Plugin<S> {

    Verdict evaluate(Listing listing, S settings);
}
