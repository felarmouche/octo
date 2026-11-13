package com.felarmouche.octo.core.shared;

import java.util.Objects;

public record Verdict(Decision decision, String reason) {

    public Verdict{
        Objects.requireNonNull(decision);
        Objects.requireNonNull(reason);
    }

    public enum Decision{
        BUY,
        NO_BUY,
        UNSURE,
        NOT_EVALUATABLE
    }

}
