package com.felarmouche.octo.core.domain;

/** The outcome of a {@link Verdict}. NOT_EVALUATABLE means "retry on a later run". */
public enum Decision {
    BUY("Kaufen"),
    UNSURE("Unsicher"),
    NO_BUY("Nicht kaufen"),
    NOT_EVALUATABLE("Nicht bewertbar");

    private final String label;

    Decision(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
