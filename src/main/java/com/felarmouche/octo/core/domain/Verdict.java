package com.felarmouche.octo.core.domain;

import java.util.List;
import java.util.Objects;

/** The judgement attached to a Listing: a Decision, a reason and the evaluation details. */
public record Verdict(Decision decision, String reason, List<LabelledValue> details) {

    public Verdict {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
        details = details == null ? List.of() : List.copyOf(details);
    }

    public Verdict(Decision decision, String reason) {
        this(decision, reason, List.of());
    }
}
