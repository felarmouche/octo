package com.felarmouche.octo.core.listingtype;

import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;

import java.util.List;

/** The category of item a Listing describes. Each Search has exactly one. */
public interface ListingType {

    String id();

    /** Fills unknown attributes from title and description. Applied to every found Listing. */
    Listing complete(Listing listing);

    /** The Listing's attributes as labelled values for Alerts. */
    List<LabelledValue> describe(Listing listing);
}
