package com.felarmouche.octo.types.generic;

import com.felarmouche.octo.core.listingtype.PriceRange;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** @param price optional price range; null means any price */
public record GenericFilterSettings(@Schema(title = "Preis") @Valid PriceRange price) {

    public static final GenericFilterSettings NONE = new GenericFilterSettings(PriceRange.ANY);

    public GenericFilterSettings {
        price = price == null ? PriceRange.ANY : price;
    }
}
