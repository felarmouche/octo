package com.felarmouche.octo.types.phone;

/** The state of a phone in Octo's own scale, independent of any marketplace's or buyback service's wording. */
public enum PhoneCondition {
    NEW("Neu"),
    VERY_GOOD("Sehr gut"),
    GOOD("Gut"),
    OK("In Ordnung"),
    DEFECT("Defekt");

    private final String label;

    PhoneCondition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
