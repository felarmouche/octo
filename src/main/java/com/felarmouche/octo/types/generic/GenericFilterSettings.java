package com.felarmouche.octo.types.generic;

import com.felarmouche.octo.core.listingtype.PriceRange;

/** @param price optional price range; null means any price */
public record GenericFilterSettings(PriceRange price) {

    public static final GenericFilterSettings NONE = new GenericFilterSettings(PriceRange.ANY);

    public GenericFilterSettings {
        price = price == null ? PriceRange.ANY : price;
    }
}
