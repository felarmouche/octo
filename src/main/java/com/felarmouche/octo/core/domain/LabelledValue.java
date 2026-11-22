package com.felarmouche.octo.core.domain;

import java.util.Objects;

/** One labelled value shown to the user, e.g. "Speicher: 128 GB" or "Rebuy zahlt: 310 €". */
public record LabelledValue(String label, String value) {
    public LabelledValue {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(value, "value");
    }
}
