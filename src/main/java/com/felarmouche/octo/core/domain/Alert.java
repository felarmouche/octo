package com.felarmouche.octo.core.domain;

import java.util.List;
import java.util.Objects;

/**
 * One message to the user about one Listing. The labelled values come from the Listing Type's
 * display and the Verdict details, so a Notifier renders them without knowing any Listing Type.
 */
public record Alert(Listing listing, Verdict verdict, List<LabelledValue> values) {
    public Alert {
        Objects.requireNonNull(listing, "listing");
        Objects.requireNonNull(verdict, "verdict");
        values = values == null ? List.of() : List.copyOf(values);
    }
}
