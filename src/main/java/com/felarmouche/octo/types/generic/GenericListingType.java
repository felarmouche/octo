package com.felarmouche.octo.types.generic;

import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.listingtype.ListingType;

import java.util.List;

/** Only what every Listing has (title, price, description, images). Watches anything, e.g. "PS5 under 250 €". */
public class GenericListingType implements ListingType {

    public static final String ID = "generic";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Listing complete(Listing listing) {
        return listing;
    }

    @Override
    public List<LabelledValue> describe(Listing listing) {
        return List.of();
    }
}
