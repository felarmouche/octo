package com.felarmouche.octo.core.domain;

/** The outcome of a {@link Verdict}. NOT_EVALUATABLE means "retry on a later run". */
public enum Decision {
    BUY,
    NO_BUY,
    UNSURE,
    NOT_EVALUATABLE
}
